package ken.mizoguch.webviewer.llm.core;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * the operations of the model on flat arrays of floats : a matrix of rows and columns is kept in
 * one array, row by row, so the model reads and writes one array instead of an array of arrays,
 * which keeps the copies of the weights of a training small.
 *
 * The methods write into an array given by the caller, so a step of a training allocates nothing
 * but the activations of the step.
 *
 * A large work is read by several threads when it is worth their call : the units of a matrix are
 * cut in parts and a unit is read by one thread only, with the values of the unit read in their own
 * order, so the parallel work writes the same bits as the same work on one thread and the answer of
 * a training does not change with the number of the threads. The system property
 * webviewer.llm.threads gives the number of the threads the module may use, and zero or one leaves
 * every operation on the thread which asks it. The threads are the threads the language shares, so
 * the module keeps no thread of its own when the plug-in is left.
 *
 * @author mizoguch-ken
 */
public final class Tensor {

    /**
     * the number of the threads a work of the matrices may be cut in
     */
    private static final int THREADS = threads();

    /**
     * the values of a part of a matrix : a work which holds less values than that keeps the thread
     * which asks it, because the call of a thread costs more than the values
     */
    static final long PART_VALUES = 1L << 17;

    private static final int TILE_N = 256;
    private static final int MV_TILE_N = 1024;

    /**
     * the operations of the module are read on a thread, never on an object of the class
     */
    private Tensor() {
    }

    /**
     * the number of the threads the system gives to the module
     *
     * @return the number of the threads, at least one
     */
    private static int threads() {
        return Math.max(1, Integer.getInteger("webviewer.llm.threads",
                ForkJoinPool.commonPool().getParallelism() + 1));
    }

    /**
     * the values of a part of a work
     */
    interface Block {

        /**
         * read the units of the part
         *
         * @param from first unit of the part
         * @param to unit after the last one
         */
        void run(int from, int to);
    }

    /**
     * the parts a work of some units is cut in
     *
     * @param units units of the work
     * @param values values read and written by the whole work
     * @return the parts of the work, one when a thread is not worth the work
     */
    private static int parts(int units, long values) {
        if ((units < 2) || (values < PART_VALUES)) {
            return 1;
        }
        return (int) Math.max(1L, Math.min((long) THREADS, Math.min(values / PART_VALUES, units)));
    }

    /**
     * the units of a work read by the threads of the module : the caller reads the last part of the
     * work by itself and waits the other parts, so a step of a training waits its own answer as it
     * does on one thread.
     *
     * @param units units of the work, a row or a column of a matrix most often
     * @param values values read and written by the whole work
     * @param action action on the units of a part
     */
    static void blocks(int units, long values, Block action) {
        if (units <= 0) {
            return;
        }
        int parts = parts(units, values);

        if (parts < 2) {
            action.run(0, units);
            return;
        }
        int size = (units + parts - 1) / parts;
        java.util.List<ForkJoinTask<?>> tasks = new java.util.ArrayList<>(parts - 1);

        for (int part = 0; part < (parts - 1); part++) {
            int from = Math.min(units, part * size);
            int to = Math.min(units, from + size);

            if (from < to) {
                tasks.add(ForkJoinTask.adapt(() -> action.run(from, to)));
            }
        }
        for (ForkJoinTask<?> task : tasks) {
            ForkJoinPool.commonPool().execute(task);
        }
        int tailFrom = Math.min(units, (parts - 1) * size);
        if (tailFrom < units) {
            action.run(tailFrom, units);
        }
        for (ForkJoinTask<?> task : tasks) {
            task.join();
        }
    }

    /**
     * the factor of the approximation of the error function used by the activation of the model
     */
    private static final double GELU_C0 = 0.7978845608028654d;   // sqrt(2 / pi)
    private static final double GELU_C1 = 0.044715d;

    /**
     * the smallest value a division of the layer normalization accepts
     */
    private static final float EPSILON = 1e-5f;

    /**
     * out = a * b , a is m rows of k values, b is k rows of n values
     *
     * @param a first matrix
     * @param m rows of a
     * @param k columns of a and rows of b
     * @param b second matrix
     * @param n columns of b
     * @param out m rows of n values, an other array than a and b
     */
    public static void ab(float[] a, int m, int k, float[] b, int n, float[] out) {
        blocks(m, (long) m * k * n, (from, to) -> {
            java.util.Arrays.fill(out, from * n, to * n, 0f);
            for (int colBase = 0; colBase < n; colBase += TILE_N) {
                int colEnd = Math.min(n, colBase + TILE_N);
                int row = from;

                for (; (row + 4) <= to; row += 4) {
                    int a0 = row * k;
                    int a1 = a0 + k;
                    int a2 = a1 + k;
                    int a3 = a2 + k;
                    int o0 = row * n;
                    int o1 = o0 + n;
                    int o2 = o1 + n;
                    int o3 = o2 + n;

                    for (int inner = 0; inner < k; inner++) {
                        float v0 = a[a0 + inner];
                        float v1 = a[a1 + inner];
                        float v2 = a[a2 + inner];
                        float v3 = a[a3 + inner];

                        if ((v0 == 0f) && (v1 == 0f) && (v2 == 0f) && (v3 == 0f)) {
                            continue;
                        }
                        int bOffset = inner * n;

                        for (int column = colBase; column < colEnd; column++) {
                            float bVal = b[bOffset + column];

                            out[o0 + column] += v0 * bVal;
                            out[o1 + column] += v1 * bVal;
                            out[o2 + column] += v2 * bVal;
                            out[o3 + column] += v3 * bVal;
                        }
                    }
                }
                for (; row < to; row++) {
                    int aOffset = row * k;
                    int outOffset = row * n;

                    for (int inner = 0; inner < k; inner++) {
                        float value = a[aOffset + inner];

                        if (value == 0f) {
                            continue;
                        }
                        int bOffset = inner * n;

                        for (int column = colBase; column < colEnd; column++) {
                            out[outOffset + column] += value * b[bOffset + column];
                        }
                    }
                }
            }
        });
    }

    /**
     * out = a' * b , a is kept as k rows of m values and is read transposed
     *
     * @param a first matrix kept transposed, k rows of m values
     * @param m rows of the transposed a and columns of b
     * @param k columns of the transposed a and rows of b
     * @param b second matrix, k rows of n values
     * @param n columns of b
     * @param out m rows of n values
     */
    public static void atb(float[] a, int m, int k, float[] b, int n, float[] out) {
        atbCore(a, m, k, b, n, out, false);
    }

    static void atbAdd(float[] a, int m, int k, float[] b, int n, float[] out) {
        atbCore(a, m, k, b, n, out, true);
    }

    private static void atbCore(float[] a, int m, int k, float[] b, int n, float[] out,
            boolean accumulate) {
        blocks(m, (long) m * k * n, (from, to) -> {
            if (!accumulate) {
                java.util.Arrays.fill(out, from * n, to * n, 0f);
            }
            for (int colBase = 0; colBase < n; colBase += TILE_N) {
                int colEnd = Math.min(n, colBase + TILE_N);
                int row = from;

                for (; (row + 4) <= to; row += 4) {
                    int o0 = row * n;
                    int o1 = o0 + n;
                    int o2 = o1 + n;
                    int o3 = o2 + n;

                    for (int inner = 0; inner < k; inner++) {
                        int aBase = (inner * m) + row;
                        float v0 = a[aBase];
                        float v1 = a[aBase + 1];
                        float v2 = a[aBase + 2];
                        float v3 = a[aBase + 3];

                        if ((v0 == 0f) && (v1 == 0f) && (v2 == 0f) && (v3 == 0f)) {
                            continue;
                        }
                        int bOffset = inner * n;

                        for (int column = colBase; column < colEnd; column++) {
                            float bVal = b[bOffset + column];

                            out[o0 + column] += v0 * bVal;
                            out[o1 + column] += v1 * bVal;
                            out[o2 + column] += v2 * bVal;
                            out[o3 + column] += v3 * bVal;
                        }
                    }
                }
                for (; row < to; row++) {
                    int outOffset = row * n;

                    for (int inner = 0; inner < k; inner++) {
                        float value = a[(inner * m) + row];

                        if (value == 0f) {
                            continue;
                        }
                        int bOffset = inner * n;

                        for (int column = colBase; column < colEnd; column++) {
                            out[outOffset + column] += value * b[bOffset + column];
                        }
                    }
                }
            }
        });
    }

    /**
     * out = a * b' , b is kept as n rows of k values and is read transposed
     *
     * @param a first matrix, m rows of k values
     * @param m rows of a
     * @param k columns of a and columns of b
     * @param b second matrix kept transposed, n rows of k values
     * @param n rows of the transposed b
     * @param out m rows of n values
     */
    public static void abt(float[] a, int m, int k, float[] b, int n, float[] out) {
        blocks(m, (long) m * n * k, (from, to) -> {
            int row = from;

            for (; (row + 2) <= to; row += 2) {
                int a0 = row * k;
                int a1 = a0 + k;
                int o0 = row * n;
                int o1 = o0 + n;
                int other = 0;

                for (; (other + 4) <= n; other += 4) {
                    int b0 = other * k;
                    int b1 = b0 + k;
                    int b2 = b1 + k;
                    int b3 = b2 + k;
                    double s00 = 0d;
                    double s01 = 0d;
                    double s02 = 0d;
                    double s03 = 0d;
                    double s10 = 0d;
                    double s11 = 0d;
                    double s12 = 0d;
                    double s13 = 0d;

                    for (int inner = 0; inner < k; inner++) {
                        double av0 = a[a0 + inner];
                        double av1 = a[a1 + inner];
                        double bv0 = b[b0 + inner];
                        double bv1 = b[b1 + inner];
                        double bv2 = b[b2 + inner];
                        double bv3 = b[b3 + inner];

                        s00 += av0 * bv0;
                        s01 += av0 * bv1;
                        s02 += av0 * bv2;
                        s03 += av0 * bv3;
                        s10 += av1 * bv0;
                        s11 += av1 * bv1;
                        s12 += av1 * bv2;
                        s13 += av1 * bv3;
                    }
                    out[o0 + other] = (float) s00;
                    out[o0 + other + 1] = (float) s01;
                    out[o0 + other + 2] = (float) s02;
                    out[o0 + other + 3] = (float) s03;
                    out[o1 + other] = (float) s10;
                    out[o1 + other + 1] = (float) s11;
                    out[o1 + other + 2] = (float) s12;
                    out[o1 + other + 3] = (float) s13;
                }
                for (; other < n; other++) {
                    int bOffset = other * k;
                    double s0 = 0d;
                    double s1 = 0d;

                    for (int inner = 0; inner < k; inner++) {
                        double bv = b[bOffset + inner];

                        s0 += a[a0 + inner] * bv;
                        s1 += a[a1 + inner] * bv;
                    }
                    out[o0 + other] = (float) s0;
                    out[o1 + other] = (float) s1;
                }
            }
            for (; row < to; row++) {
                int aOffset = row * k;
                int outOffset = row * n;
                int other = 0;

                for (; (other + 4) <= n; other += 4) {
                    int b0 = other * k;
                    int b1 = b0 + k;
                    int b2 = b1 + k;
                    int b3 = b2 + k;
                    double s0 = 0d;
                    double s1 = 0d;
                    double s2 = 0d;
                    double s3 = 0d;

                    for (int inner = 0; inner < k; inner++) {
                        double av = a[aOffset + inner];

                        s0 += av * b[b0 + inner];
                        s1 += av * b[b1 + inner];
                        s2 += av * b[b2 + inner];
                        s3 += av * b[b3 + inner];
                    }
                    out[outOffset + other] = (float) s0;
                    out[outOffset + other + 1] = (float) s1;
                    out[outOffset + other + 2] = (float) s2;
                    out[outOffset + other + 3] = (float) s3;
                }
                for (; other < n; other++) {
                    int bOffset = other * k;
                    double sum = 0d;

                    for (int inner = 0; inner < k; inner++) {
                        sum += (double) a[aOffset + inner] * b[bOffset + inner];
                    }
                    out[outOffset + other] = (float) sum;
                }
            }
        });
    }

    /**
     * out = vec * mat , vec has k values and mat is k rows of n values
     *
     * @param vec input vector of length k
     * @param k length of vec and rows of mat
     * @param mat weight matrix kept as k rows of n values
     * @param n columns of mat and length of out
     * @param out vector of n values written
     */
    public static void mv(float[] vec, int k, float[] mat, int n, float[] out) {
        blocks(n, (long) k * n, (from, to) -> {
            java.util.Arrays.fill(out, from, to, 0f);
            for (int colBase = from; colBase < to; colBase += MV_TILE_N) {
                int colEnd = Math.min(to, colBase + MV_TILE_N);

                for (int inner = 0; inner < k; inner++) {
                    float value = vec[inner];

                    if (value == 0f) {
                        continue;
                    }
                    int rowOffset = inner * n;

                    for (int column = colBase; column < colEnd; column++) {
                        out[column] += value * mat[rowOffset + column];
                    }
                }
            }
        });
    }

    static void add(float[] dst, float[] src, int length) {
        for (int index = 0; index < length; index++) {
            dst[index] += src[index];
        }
    }

    /**
     * add a vector to every row of a matrix : the bias of a projection
     *
     * @param matrix matrix to add to, rows of columns values
     * @param rows rows of the matrix
     * @param columns columns of the matrix
     * @param bias vector of the columns values
     */
    public static void addBias(float[] matrix, int rows, int columns, float[] bias) {
        blocks(rows, (long) rows * columns, (from, to) -> {
            for (int row = from; row < to; row++) {
                int offset = row * columns;

                for (int column = 0; column < columns; column++) {
                    matrix[offset + column] += bias[column];
                }
            }
        });
    }

    /**
     * add a matrix to an other one
     *
     * @param matrix matrix to add to
     * @param other matrix to add
     */
    public static void add(float[] matrix, float[] other) {
        for (int index = 0; index < matrix.length; index++) {
            matrix[index] += other[index];
        }
    }

    /**
     * the sum of the rows of a matrix, column by column : the gradient of a bias
     *
     * @param matrix matrix to read, rows of columns values
     * @param rows rows of the matrix
     * @param columns columns of the matrix
     * @param out columns values written
     */
    public static void columnSum(float[] matrix, int rows, int columns, float[] out) {
        columnSumCore(matrix, rows, columns, out, false);
    }

    static void columnSumAdd(float[] matrix, int rows, int columns, float[] out) {
        columnSumCore(matrix, rows, columns, out, true);
    }

    private static void columnSumCore(float[] matrix, int rows, int columns, float[] out,
            boolean accumulate) {
        blocks(columns, (long) rows * columns, (from, to) -> {
            if (!accumulate) {
                for (int column = from; column < to; column++) {
                    out[column] = 0f;
                }
            }
            for (int row = 0; row < rows; row++) {
                int offset = (row * columns) + from;

                for (int column = from; column < to; column++) {
                    out[column] += matrix[offset++];
                }
            }
        });
    }

    /**
     * write zero in every value of an array
     *
     * @param array array to clear
     */
    public static void zero(float[] array) {
        java.util.Arrays.fill(array, 0f);
    }

    /**
     * write zero in some values of an array
     *
     * @param array array to clear
     * @param offset first value to clear
     * @param size values to clear
     */
    public static void zeroSlice(float[] array, int offset, int size) {
        java.util.Arrays.fill(array, offset, offset + size, 0f);
    }

    /**
     * copy an array into an other one
     *
     * @param from array to read
     * @param to array to write, of the same length
     */
    public static void copy(float[] from, float[] to) {
        System.arraycopy(from, 0, to, 0, from.length);
    }

    static void copy(float[] from, float[] to, int length) {
        System.arraycopy(from, 0, to, 0, length);
    }

    /**
     * the activation of the network of the model : a smooth version of the rectifier, which lets
     * the small negative values pass instead of cutting them
     *
     * @param values values to change
     */
    public static void gelu(float[] values) {
        gelu(values, values.length);
    }

    static void gelu(float[] values, int length) {
        blocks(length, length, (from, to) -> {
            for (int index = from; index < to; index++) {
                values[index] = geluValue(values[index]);
            }
        });
    }

    /**
     * the activation of one value
     *
     * @param value value to change
     * @return the activated value
     */
    public static float geluValue(float value) {
        if (Float.isNaN(value)) {
            return Float.NaN;
        }
        if (value >= 10f) {
            return value;
        }
        if (value <= -10f) {
            return 0f;
        }
        double x = value;
        double inner = GELU_C0 * (x + (GELU_C1 * x * x * x));

        return (float) (0.5d * x * (1d + Math.tanh(inner)));
    }

    /**
     * the derivative of the activation, read from the value before the activation
     *
     * @param before values before the activation
     * @param out derivatives written
     */
    public static void geluDerivative(float[] before, float[] out) {
        geluDerivative(before, out, before.length);
    }

    static void geluDerivative(float[] before, float[] out, int length) {
        blocks(length, length, (from, to) -> {
            for (int index = from; index < to; index++) {
                float value = before[index];

                if (Float.isNaN(value)) {
                    out[index] = Float.NaN;
                } else if (value >= 10f) {
                    out[index] = 1f;
                } else if (value <= -10f) {
                    out[index] = 0f;
                } else {
                    double x = value;
                    double x2 = x * x;
                    double inner = GELU_C0 * (x + (GELU_C1 * x2 * x));
                    double tanh = Math.tanh(inner);
                    double outer = 1d - (tanh * tanh);
                    double innerDerivative = GELU_C0 * (1d + (3d * GELU_C1 * x2));

                    out[index] = (float) ((0.5d * (1d + tanh)) + (0.5d * x * outer * innerDerivative));
                }
            }
        });
    }

    /**
     * copy some columns of a matrix into their own matrix : one head of the attention reads its own
     * part of the values of the model
     *
     * @param matrix matrix to read, rows of columns values
     * @param rows rows of the matrix
     * @param columns columns of the matrix
     * @param offset first column to read
     * @param width columns to read
     * @param out rows of width values written
     */
    public static void gather(float[] matrix, int rows, int columns, int offset, int width,
            float[] out) {
        blocks(rows, (long) rows * width, (first, last) -> {
            for (int row = first; row < last; row++) {
                int start = (row * columns) + offset;

                System.arraycopy(matrix, start, out, row * width, width);
            }
        });
    }

    /**
     * write a matrix into some columns of an other one
     *
     * @param source matrix to read, rows of width values
     * @param rows rows of the matrices
     * @param width columns of the source
     * @param offset first column to write in the matrix
     * @param columns columns of the matrix to write
     * @param matrix matrix to write
     */
    public static void scatter(float[] source, int rows, int width, int offset, int columns,
            float[] matrix) {
        blocks(rows, (long) rows * width, (first, last) -> {
            for (int row = first; row < last; row++) {
                System.arraycopy(source, row * width, matrix, (row * columns) + offset, width);
            }
        });
    }

    /**
     * the probabilities of a row of values : every value is divided by the sum of the row, so the
     * row reads as a distribution of the choices
     *
     * @param values values of the row, changed in place
     * @param offset first value of the row
     * @param size values of the row
     */
    public static void softmax(float[] values, int offset, int size) {
        if (size <= 0) {
            return;
        }
        boolean hasNaN = false;
        int posInfCount = 0;
        boolean hasFinite = false;
        double largest = Double.NEGATIVE_INFINITY;

        for (int index = 0; index < size; index++) {
            float value = values[offset + index];

            if (Float.isNaN(value)) {
                hasNaN = true;
                break;
            }
            if (value == Float.POSITIVE_INFINITY) {
                posInfCount++;
            } else if (Float.isFinite(value)) {
                if (!hasFinite || (value > largest)) {
                    largest = value;
                    hasFinite = true;
                }
            }
        }
        if (hasNaN) {
            java.util.Arrays.fill(values, offset, offset + size, Float.NaN);
            return;
        }
        if (posInfCount > 0) {
            float share = (float) (1d / posInfCount);

            for (int index = 0; index < size; index++) {
                values[offset + index] = (values[offset + index] == Float.POSITIVE_INFINITY)
                        ? share : 0f;
            }
            return;
        }
        if (!hasFinite) {
            java.util.Arrays.fill(values, offset, offset + size, (float) (1d / size));
            return;
        }
        double sum = 0d;

        for (int index = 0; index < size; index++) {
            double exponential = Math.exp((double) values[offset + index] - largest);

            values[offset + index] = (float) exponential;
            sum += exponential;
        }
        double invSum = 1d / sum;

        for (int index = 0; index < size; index++) {
            values[offset + index] = (float) (values[offset + index] * invSum);
        }
    }

    /**
     * the normalization of the rows of a matrix : every row is centered and reduced, then scaled
     * and shifted by the weights of the model
     *
     * @param matrix rows of the values to normalize, rows of width values
     * @param rows rows of the matrix
     * @param width values of a row
     * @param scale weights of the model which scale the row, may be null for none
     * @param shift weights of the model which shift the row, may be null for none
     * @param out normalized rows written
     * @param centered the rows reduced and centered, kept for the backward step, may be null
     * @param inverse the inverse of the standard deviation of every row, may be null
     */
    public static void layerNorm(float[] matrix, int rows, int width, float[] scale, float[] shift,
            float[] out, float[] centered, float[] inverse) {
        blocks(rows, (long) rows * width, (first, last) -> {
            for (int row = first; row < last; row++) {
                int offset = row * width;
                double sum = 0d;

                for (int column = 0; column < width; column++) {
                    sum += matrix[offset + column];
                }
                double mean = sum / width;
                double variance = 0d;

                for (int column = 0; column < width; column++) {
                    double difference = matrix[offset + column] - mean;

                    variance += difference * difference;
                }
                double inv = 1d / Math.sqrt((variance / width) + EPSILON);

                for (int column = 0; column < width; column++) {
                    double reduced = (matrix[offset + column] - mean) * inv;

                    if (centered != null) {
                        centered[offset + column] = (float) reduced;
                    }
                    out[offset + column] = (float) ((reduced * ((scale == null) ? 1f : scale[column]))
                            + ((shift == null) ? 0f : shift[column]));
                }
                if (inverse != null) {
                    inverse[row] = (float) inv;
                }
            }
        });
    }

    /**
     * the gradient which goes back through a normalization : the gradient of the input rows is
     * added to the array given, which holds the residual path of the model
     *
     * @param rows rows of the values which were normalized
     * @param width values of a row
     * @param centered the reduced rows kept by the normalization
     * @param inverse the inverse of the standard deviation of every row
     * @param scale weights of the model which scaled the rows
     * @param gradient gradient of the normalized rows
     * @param out gradient of the input rows, added to
     * @param scaleGradient gradient of the scale weights, added to
     * @param shiftGradient gradient of the shift weights, written
     */
    public static void layerNormBackward(int rows, int width, float[] centered,
            float[] inverse, float[] scale, float[] gradient, float[] out, float[] scaleGradient,
            float[] shiftGradient) {
        layerNormBackward(rows, width, centered, inverse, scale, gradient, out, scaleGradient,
                shiftGradient, false);
    }

    static void layerNormBackward(int rows, int width, float[] centered,
            float[] inverse, float[] scale, float[] gradient, float[] out, float[] scaleGradient,
            float[] shiftGradient, boolean accumulate) {
        if ((shiftGradient != null) && (scaleGradient != null)) {
            if (accumulate) {
                columnSumAdd(gradient, rows, width, shiftGradient);
            } else {
                columnSum(gradient, rows, width, shiftGradient);
            }
            blocks(width, (long) rows * width, (from, to) -> {
                for (int row = 0; row < rows; row++) {
                    int offset = row * width;

                    for (int column = from; column < to; column++) {
                        scaleGradient[column] += gradient[offset + column] * centered[offset + column];
                    }
                }
            });
        }
        blocks(rows, (long) rows * width, (first, last) -> {
            for (int row = first; row < last; row++) {
                int offset = row * width;
                double sum = 0d;
                double dot = 0d;

                for (int column = 0; column < width; column++) {
                    double scaled = (double) gradient[offset + column] * scale[column];

                    sum += scaled;
                    dot += scaled * centered[offset + column];
                }
                double mean = sum / width;
                double meanDot = dot / width;
                double inv = inverse[row];

                for (int column = 0; column < width; column++) {
                    double scaled = (double) gradient[offset + column] * scale[column];

                    out[offset + column] += (float) (inv * (scaled - mean
                            - (centered[offset + column] * meanDot)));
                }
            }
        });
    }

    /**
     * the size of the gradients of a model : the gradients of a step which is too large are cut, so
     * a training does not run away
     *
     * @param gradients the gradients of every weight of the model
     * @return the norm of the gradients
     */
    public static double norm(float[][] gradients) {
        double sum = 0d;

        for (float[] gradient : gradients) {
            for (float value : gradient) {
                sum += (double) value * value;
            }
        }
        return Math.sqrt(sum);
    }
}

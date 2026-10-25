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
    private static final long PART_VALUES = 1L << 13;

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
        int parts = parts(units, values);

        if (parts < 2) {
            action.run(0, units);
            return;
        }
        int size = (units + parts - 1) / parts;
        ForkJoinTask<?>[] tasks = new ForkJoinTask<?>[parts - 1];

        for (int part = 0; part < (parts - 1); part++) {
            int from = part * size;
            int to = Math.min(units, from + size);

            tasks[part] = ForkJoinTask.adapt(() -> action.run(from, to));
        }
        for (ForkJoinTask<?> task : tasks) {
            ForkJoinPool.commonPool().execute(task);
        }
        action.run((parts - 1) * size, units);
        for (ForkJoinTask<?> task : tasks) {
            task.join();
        }
    }

    /**
     * the factor of the approximation of the error function used by the activation of the model
     */
    private static final float GELU_C0 = 0.7978845608028654f;   // sqrt(2 / pi)
    private static final float GELU_C1 = 0.044715f;

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
        for (int index = 0; index < (m * n); index++) {
            out[index] = 0f;
        }
        blocks(m, (long) m * k * n, (from, to) -> {
            for (int row = from; row < to; row++) {
                int aOffset = row * k;
                int outOffset = row * n;

                for (int inner = 0; inner < k; inner++) {
                    float value = a[aOffset + inner];

                    if (value == 0f) {
                        continue;
                    }
                    int bOffset = inner * n;

                    for (int column = 0; column < n; column++) {
                        out[outOffset + column] += value * b[bOffset + column];
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
        for (int index = 0; index < (m * n); index++) {
            out[index] = 0f;
        }
        blocks(n, (long) m * k * n, (from, to) -> {
            for (int inner = 0; inner < k; inner++) {
                int aOffset = inner * m;
                int bOffset = inner * n;

                for (int row = 0; row < m; row++) {
                    float value = a[aOffset + row];

                    if (value == 0f) {
                        continue;
                    }
                    int outOffset = row * n;

                    for (int column = from; column < to; column++) {
                        out[outOffset + column] += value * b[bOffset + column];
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
        for (int index = 0; index < (m * n); index++) {
            out[index] = 0f;
        }
        blocks(m, (long) m * n * k, (from, to) -> {
            for (int row = from; row < to; row++) {
                int aOffset = row * k;
                int outOffset = row * n;

                for (int other = 0; other < n; other++) {
                    int bOffset = other * k;
                    float sum = 0f;

                    for (int inner = 0; inner < k; inner++) {
                        sum += a[aOffset + inner] * b[bOffset + inner];
                    }
                    out[outOffset + other] = sum;
                }
            }
        });
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
        blocks(columns, (long) rows * columns, (from, to) -> {
            for (int column = from; column < to; column++) {
                out[column] = 0f;
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
        for (int index = 0; index < array.length; index++) {
            array[index] = 0f;
        }
    }

    /**
     * write zero in some values of an array
     *
     * @param array array to clear
     * @param offset first value to clear
     * @param size values to clear
     */
    public static void zeroSlice(float[] array, int offset, int size) {
        for (int index = 0; index < size; index++) {
            array[offset + index] = 0f;
        }
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

    /**
     * the activation of the network of the model : a smooth version of the rectifier, which lets
     * the small negative values pass instead of cutting them
     *
     * @param values values to change
     */
    public static void gelu(float[] values) {
        blocks(values.length, values.length, (from, to) -> {
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
        float inner = GELU_C0 * (value + (GELU_C1 * value * value * value));

        return 0.5f * value * (1f + (float) Math.tanh(inner));
    }

    /**
     * the derivative of the activation, read from the value before the activation
     *
     * @param before values before the activation
     * @param out derivatives written
     */
    public static void geluDerivative(float[] before, float[] out) {
        blocks(before.length, before.length, (from, to) -> {
            for (int index = from; index < to; index++) {
                float value = before[index];
                float inner = GELU_C0 * (value + (GELU_C1 * value * value * value));
                float tanh = (float) Math.tanh(inner);
                float outer = 1f - (tanh * tanh);
                float innerDerivative = GELU_C0 * (1f + (3f * GELU_C1 * value * value));

                out[index] = 0.5f * (1f + tanh) + (0.5f * value * outer * innerDerivative);
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
        float largest = values[offset];

        for (int index = 1; index < size; index++) {
            if (values[offset + index] > largest) {
                largest = values[offset + index];
            }
        }
        float sum = 0f;

        for (int index = 0; index < size; index++) {
            float exponential = (float) Math.exp(values[offset + index] - largest);

            values[offset + index] = exponential;
            sum += exponential;
        }
        if (sum <= 0f) {
            sum = EPSILON;
        }
        for (int index = 0; index < size; index++) {
            values[offset + index] /= sum;
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
                float sum = 0f;

                for (int column = 0; column < width; column++) {
                    sum += matrix[offset + column];
                }
                float mean = sum / width;
                float variance = 0f;

                for (int column = 0; column < width; column++) {
                    float difference = matrix[offset + column] - mean;

                    variance += difference * difference;
                }
                float divisor = (float) Math.sqrt((variance / width) + EPSILON);

                for (int column = 0; column < width; column++) {
                    float reduced = (matrix[offset + column] - mean) / divisor;

                    if (centered != null) {
                        centered[offset + column] = reduced;
                    }
                    out[offset + column] = (reduced * ((scale == null) ? 1f : scale[column]))
                            + ((shift == null) ? 0f : shift[column]);
                }
                if (inverse != null) {
                    inverse[row] = 1f / divisor;
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
        columnSum(gradient, rows, width, shiftGradient);
        for (int index = 0; index < centered.length; index++) {
            scaleGradient[index % width] += gradient[index] * centered[index];
        }
        blocks(rows, (long) rows * width, (first, last) -> {
            for (int row = first; row < last; row++) {
                int offset = row * width;
                float sum = 0f;
                float dot = 0f;

                for (int column = 0; column < width; column++) {
                    float scaled = gradient[offset + column] * scale[column];

                    sum += scaled;
                    dot += scaled * centered[offset + column];
                }
                float mean = sum / width;
                float meanDot = dot / width;
                float inv = inverse[row];

                for (int column = 0; column < width; column++) {
                    float scaled = gradient[offset + column] * scale[column];

                    out[offset + column] += inv * (scaled - mean
                            - (centered[offset + column] * meanDot));
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

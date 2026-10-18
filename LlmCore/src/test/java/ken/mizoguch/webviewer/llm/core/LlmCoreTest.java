package ken.mizoguch.webviewer.llm.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author mizoguch-ken
 */
class LlmCoreTest {

    @TempDir
    Path tempDir;

    @Test
    void theProductsOfTheTensorReadTheSameValues() {
        float[] left = { 1f, 2f, 3f, 4f, 5f, 6f };
        float[] right = { 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f };
        float[] out = new float[9];

        Tensor.ab(left, 2, 3, right, 3, out);
        assertEquals(66f, out[0], 1e-4f);
        assertEquals(186f, out[5], 1e-4f);

        out = new float[9];
        Tensor.atb(left, 3, 2, right, 3, out);
        assertEquals(47f, out[0], 1e-4f);
        assertEquals(99f, out[8], 1e-4f);

        out = new float[9];
        Tensor.abt(left, 2, 3, right, 3, out);
        assertEquals(50f, out[0], 1e-4f);
        assertEquals(212f, out[5], 1e-4f);
    }

    @Test
    void theSoftmaxOfARowSumsToOne() {
        float[] row = { 3f, 1f, 2f, -4f };

        Tensor.softmax(row, 0, row.length);

        float sum = 0f;

        for (float value : row) {
            sum += value;
        }
        assertEquals(1f, sum, 1e-5f);
        // the largest value of the row keeps the largest share of the sum
        assertEquals(0.664838f, row[0], 1e-5f);
        assertTrue(row[3] < 1e-3f);
    }

    @Test
    void aByteTokenizerReadsAnyText() {
        ByteTokenizer tokenizer = new ByteTokenizer();

        assertEquals(257, tokenizer.size());
        assertEquals(256, tokenizer.end());
        assertEquals("ab", tokenizer.decode(tokenizer.encode("ab")));
        // a character of several bytes is read as the bytes of its utf-8 text
        assertEquals(3, tokenizer.encode("\u3042").length);
        assertEquals("\u3042", tokenizer.decode(tokenizer.encode("\u3042")));
    }

    @Test
    void aCharacterTokenizerKeepsOneTokenForOneCharacter() {
        CharTokenizer tokenizer = new CharTokenizer();

        tokenizer.learn("abc\u3042");
        assertEquals(6, tokenizer.size());
        assertEquals("abc\u3042", tokenizer.decode(tokenizer.encode("abc\u3042")));
        // a character the vocabulary of the model does not hold is read as the unknown token
        assertEquals(CharTokenizer.UNKNOWN, tokenizer.encode("\u3044")[0]);
    }

    @Test
    void theVocabularyOfACharacterTokenizerIsWrittenAndReadAgain() throws Exception {
        CharTokenizer written = new CharTokenizer();

        written.learn("abc\u3042");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            written.writeVocab(out);
        }
        CharTokenizer read = new CharTokenizer();

        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            read.readVocab(in, written.size());
        }
        assertEquals(written.characters(), read.characters());
        assertEquals("abc\u3042", read.decode(read.encode("abc\u3042")));
    }

    @Test
    void theSizesOfAModelMatchTheWeightsItHolds() {
        ModelConfig config = new ModelConfig();

        config.setVocabType(ModelConfig.VOCAB_CHAR);
        config.setVocabSize(60);
        config.setDModel(24);
        config.setHeads(3);
        config.setLayers(2);
        config.setContext(32);

        TransformerModel model = new TransformerModel(config);
        long values = 0L;

        for (float[] weight : model.weights()) {
            values += weight.length;
        }
        assertEquals(config.parameters(), values);
        assertEquals((2 * TransformerModel.F_LAYER_COUNT) + TransformerModel.W_LAYERS,
                model.weights().length);
    }

    @Test
    void theHeadsWhichDoNotSplitTheModelAreRefused() {
        ModelConfig config = new ModelConfig();

        config.setDModel(30);
        assertTrue(config.setHeads(3));
        assertFalse(config.setHeads(4));
    }

    @Test
    void theGradientsOfTheModelMatchTheLossItReads() {
        TransformerModel model = new TransformerModel(aModel());
        int[] tokens = { 2, 3, 4, 5, 6, 7 };

        // the weights of a new model are so small that the loss of a step moves by less than the
        // digits a float keeps : they are made larger before a difference is read of them
        for (float[] weight : model.weights()) {
            for (int index = 0; index < weight.length; index++) {
                weight[index] *= 6f;
            }
        }

        TransformerModel.Step kept = model.newStep(tokens.length, true);

        model.forward(tokens, tokens.length, kept);
        model.backward(tokens, tokens.length, kept);

        for (int leaf = 0; leaf < model.weights().length; leaf++) {
            float[] weight = model.weights()[leaf];

            for (int pick = 0; pick < Math.min(weight.length, 3); pick++) {
                double numeric = numeric(model, tokens, leaf, pick, 1e-2f);
                double analytic = model.gradients()[leaf][pick];

                if (Math.max(Math.abs(numeric), Math.abs(analytic)) < 1e-4d) {
                    continue;
                }
                double spread = Math.max(Math.abs(numeric), Math.abs(analytic));

                assertEquals(analytic, numeric, (0.05d * spread) + 1e-4d,
                        "weight " + leaf + " value " + pick);
            }
        }
    }

    @Test
    void aTrainingBringsTheLossOfTheModelDown() {
        LlmCore core = new LlmCore();

        core.newModel(aModel());

        LlmCore.TrainResult first = core.train(List.of("abcdefgh"), 2, 0.02d, null);
        LlmCore.TrainResult last = core.train(List.of("abcdefgh"), 40, 0.02d, null);

        assertTrue(last.loss < first.loss);
        assertTrue(last.steps > first.steps);
        // the model answers the text it was trained with
        LlmCore.AnswerResult answer = core.generate("abc", 14, new Sampler(), null);

        assertTrue(answer.text.startsWith("defg"));
        assertTrue(answer.ended);
    }

    @Test
    void aModelWrittenInAFileAnswersTheSameText() {
        LlmCore core = new LlmCore();

        core.newModel(aModel());
        core.train(List.of("xyzxyz"), 10, 0.02d, null);

        Path file = tempDir.resolve("model.lmc");

        core.save(file);

        LlmCore other = new LlmCore();

        other.load(file);

        assertEquals(core.tokenizer().size(), other.tokenizer().size());
        assertEquals(core.parameters(), other.parameters());
        assertEquals(core.generate("xy", 6, new Sampler(), null).text,
                other.generate("xy", 6, new Sampler(), null).text);
    }


    @Test
    void aModelTooLargeForTheComputerIsRefused() {
        ModelConfig config = new ModelConfig();

        config.setDModel(512);
        config.setHeads(16);
        config.setLayers(16);
        config.setContext(512);

        LlmCore core = new LlmCore();

        assertThrows(LlmCoreException.class, () -> core.newModel(config));
    }

    @Test
    void aTrainingOfNothingToLearnIsRefused() {
        LlmCore core = new LlmCore();

        core.newModel(aModel());
        assertThrows(LlmCoreException.class, () -> core.train(List.of(""), 2, 0.02d, null));
    }

    @Test
    void anIncorrectLearningRateIsRefused() {
        LlmCore core = new LlmCore();

        core.newModel(aModel());
        assertThrows(LlmCoreException.class,
                () -> core.train(List.of("abcdefgh"), 2, 5d, null));
    }

    @Test
    void aCoreWithoutAModelAnswersNothing() {
        LlmCore core = new LlmCore();

        assertFalse(core.hasModel());
        assertThrows(LlmCoreException.class, () -> core.generate("ab", 4, new Sampler(), null));
    }

    /**
     * the sizes of a small model, of the weights the seed always gives
     *
     * @return the sizes
     */
    private static ModelConfig aModel() {
        ModelConfig config = new ModelConfig();

        config.setVocabType(ModelConfig.VOCAB_CHAR);
        config.setDModel(32);
        config.setHeads(4);
        config.setLayers(2);
        config.setContext(32);
        config.setSeed(7L);
        return config;
    }

    /**
     * the difference of the loss when one value of a weight is moved a little
     *
     * @param model model to read
     * @param tokens tokens the model reads
     * @param leaf weight to move
     * @param pick value of the weight to move
     * @param delta how far the value is moved
     * @return the difference of the loss
     */
    private static double numeric(TransformerModel model, int[] tokens, int leaf, int pick,
            float delta) {
        float[] weight = model.weights()[leaf];
        float original = weight[pick];

        weight[pick] = original + delta;

        double up = loss(model, tokens);

        weight[pick] = original - delta;

        double down = loss(model, tokens);

        weight[pick] = original;
        return (up - down) / (2d * delta);
    }

    /**
     * the loss of the model on the tokens given, read at once
     *
     * @param model model to read
     * @param tokens tokens the model reads
     * @return the loss
     */
    private static double loss(TransformerModel model, int[] tokens) {
        int length = tokens.length;
        int v = model.vocabulary();
        TransformerModel.Step step = model.newStep(length, false);
        float[] head = model.forward(tokens, length, step);
        double total = 0d;

        for (int position = 0; position < (length - 1); position++) {
            float[] row = new float[v];

            System.arraycopy(head, position * v, row, 0, v);
            Tensor.softmax(row, 0, v);
            total -= Math.log(Math.max(row[tokens[position + 1]], 1e-9d));
        }
        return total / (length - 1);
    }
}


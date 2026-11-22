package ken.mizoguch.webviewer.llm.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
                weight[index] *= 3f;
            }
        }

        TransformerModel.Step kept = model.newStep(tokens.length, true);

        model.forward(tokens, tokens.length, kept);
        model.backward(tokens, tokens.length, kept);

        for (int leaf = 0; leaf < model.weights().length; leaf++) {
            float[] weight = model.weights()[leaf];

            for (int pick = 0; pick < Math.min(weight.length, 6); pick++) {
                double numeric = numeric(model, tokens, leaf, pick, 2e-3f);
                double analytic = model.gradients()[leaf][pick];

                if (Math.max(Math.abs(numeric), Math.abs(analytic)) < 1e-4d) {
                    continue;
                }
                double spread = Math.max(Math.abs(numeric), Math.abs(analytic));

                assertEquals(analytic, numeric, (0.03d * spread) + 5e-4d,
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

    @Test
    void samplerHandlesLargeLogitsAtLowTemperatureAndAdamWRejectsNaN() {
        Sampler sampler = new Sampler(0.01d, 0, 1d, new java.util.Random(1L));
        int picked = sampler.pick(new float[] { 1000f, 999f, 100f });
        assertEquals(0, picked);

        Sampler nanSampler = new Sampler(Double.NaN, 0, Double.NaN, new java.util.Random(1L));
        assertEquals(0d, nanSampler.temperature());
        assertEquals(1d, nanSampler.topSum());

        AdamW optimizer = new AdamW(new float[][] { new float[] { 1f, 2f } });
        assertFalse(optimizer.setLearningRate(Double.NaN));
        assertFalse(optimizer.setWeightDecay(Double.NaN));
        assertFalse(optimizer.setLargestGradient(Double.NaN));
    }

    @Test
    void loadedCharModelRefusesUnseenVocabularyOnRetrainWithoutCorruptingState() {
        LlmCore core = new LlmCore();
        core.newModel(aModel());
        core.train(List.of("abcdef"), 2, 0.02d, null);
        int savedTexts = core.texts();
        long savedTokens = core.tokens();
        Path file = tempDir.resolve("char-model.lmc");
        core.save(file);

        LlmCore reloaded = new LlmCore();
        reloaded.load(file);
        int vocabBefore = reloaded.tokenizer().size();
        assertThrows(LlmCoreException.class, () -> reloaded.train(List.of("xyz123"), 2, 0.02d, null));
        assertEquals(vocabBefore, reloaded.tokenizer().size());
        assertEquals(0, reloaded.texts());
        assertFalse(reloaded.generate("ab", 4, new Sampler(), null).text.isEmpty());
        assertTrue(Double.isFinite(reloaded.evaluate(List.of("abcdef")).loss));

        // Empty training after successful training keeps existing dataset counts intact.
        assertThrows(LlmCoreException.class, () -> core.train(List.of(""), 2, 0.02d, null));
        assertEquals(savedTexts, core.texts());
        assertEquals(savedTokens, core.tokens());
    }

    @Test
    void tokenizeDetokenizeEvaluateOptimizerPreserveAndFlushWork() {
        LlmCore core = new LlmCore();
        assertTrue(Double.isNaN(core.learningRate()));
        assertTrue(Double.isNaN(core.weightDecay()));
        assertTrue(Double.isNaN(core.largestGradient()));

        core.newModel(aModel());
        core.setLearningRate(0.025d);
        core.setWeightDecay(0.05d);
        core.setLargestGradient(0.75d);
        assertThrows(LlmCoreException.class, () -> core.setLearningRate(0d));
        assertThrows(LlmCoreException.class, () -> core.setWeightDecay(-0.1d));
        assertThrows(LlmCoreException.class, () -> core.setLargestGradient(-1d));

        LlmCore.EvalResult before = core.evaluate(List.of("abcdefgh", "abcdefgh"));
        assertTrue(Double.isFinite(before.loss));
        assertEquals(Math.exp(before.loss), before.perplexity, 1e-9d);
        assertTrue(before.tokens > 0L);
        assertTrue(before.windows > 0);
        // Evaluation before training must not pollute CharTokenizer vocabulary
        assertEquals(CharTokenizer.FIRST, core.tokenizer().size());

        core.train(List.of("abcdefgh", "abcdefgh"), 25, null, null);
        // Vocabulary expansion preserves previously configured optimizer controls
        assertEquals(0.025d, core.learningRate(), 1e-12d);
        assertEquals(0.05d, core.weightDecay(), 1e-12d);
        assertEquals(0.75d, core.largestGradient(), 1e-12d);

        int vocabAfterTrain = core.tokenizer().size();
        int[] encoded = core.tokenize("abcd");
        assertEquals(4, encoded.length);
        assertEquals("abcd", core.detokenize(encoded));
        assertEquals(0, core.tokenize("").length);
        assertEquals("", core.detokenize(new int[0]));

        LlmCore.EvalResult after = core.evaluate(List.of("abcdefgh"));
        assertTrue(after.loss < before.loss);
        assertTrue(after.perplexity < before.perplexity);
        // Evaluating unseen chars must not mutate vocabulary or change steps
        LlmCore.EvalResult unseen = core.evaluate(List.of("xyz!@#"));
        assertTrue(Double.isFinite(unseen.loss));
        assertEquals(vocabAfterTrain, core.tokenizer().size());

        // ByteTokenizer trailing incomplete multi-byte UTF-8 sequence is flushed cleanly on stop/limit
        LlmCore byteCore = new LlmCore();
        ModelConfig byteCfg = new ModelConfig();
        byteCfg.setVocabType(ModelConfig.VOCAB_BYTE);
        byteCfg.setDModel(32);
        byteCfg.setHeads(4);
        byteCfg.setLayers(2);
        byteCfg.setContext(32);
        byteCfg.setSeed(7L);
        byteCore.newModel(byteCfg);
        byteCore.train(List.of("a\u3042a\u3042a\u3042a\u3042"), 50, 0.03d, null);
        List<String> pieces = new java.util.ArrayList<>();
        LlmCore.AnswerResult partial = byteCore.generate("a", 1, new Sampler(), new LlmCoreListener() {
            @Override
            public void onToken(String piece) {
                pieces.add(piece);
            }
        });
        assertFalse(partial.text.isEmpty());
        assertEquals(String.join("", pieces), partial.text);
    }

    @Test
    void kvCacheMatchesFullForwardLogitsAndSlidingWindowGeneration() {
        ModelConfig cfg = aModel();
        cfg.setContext(16);
        TransformerModel model = new TransformerModel(cfg);
        for (float[] w : model.weights()) {
            for (int i = 0; i < w.length; i++) {
                w[i] *= 4f;
            }
        }
        int[] seq = { 2, 3, 4, 5, 6, 7, 2, 5, 3, 7, 4, 6, 2, 3, 4, 5 };
        TransformerModel.KvCache cache = model.newCache();
        for (int pos = 0; pos < seq.length; pos++) {
            float[] stepLogits = model.step(seq[pos], pos, cache);
            float[] fullLogits = model.logits(seq, pos + 1);
            assertEquals(fullLogits.length, stepLogits.length);
            for (int j = 0; j < fullLogits.length; j++) {
                assertEquals(fullLogits[j], stepLogits[j], 1e-4f,
                        "Mismatch at pos=" + pos + " vocab=" + j);
            }
        }

        // Verify full-sliding-window generation (> context tokens) matches reference loop
        LlmCore core = new LlmCore();
        ModelConfig genCfg = new ModelConfig();
        genCfg.setVocabType(ModelConfig.VOCAB_CHAR);
        genCfg.setDModel(24);
        genCfg.setHeads(4);
        genCfg.setLayers(2);
        genCfg.setContext(16);
        genCfg.setSeed(19L);
        core.newModel(genCfg);
        core.train(List.of("0123456789abcdef".repeat(4)), 2, 0.02d, null);

        LlmCore.AnswerResult generated = core.generate("0123456789", 24, new Sampler(), null);
        assertEquals(24, generated.tokens);
        assertFalse(generated.stopped);

        Path refFile = tempDir.resolve("sliding_ref.lmc");
        core.save(refFile);
        ModelStore.Content saved = ModelStore.load(refFile);
        assertTrue(saved.isLoaded());
        TransformerModel refModel = new TransformerModel(saved.config());
        for (int leaf = 0; leaf < refModel.weights().length; leaf++) {
            System.arraycopy(saved.weights()[leaf], 0, refModel.weights()[leaf], 0,
                    saved.weights()[leaf].length);
        }
        int context = refModel.context();
        int[] encoded = core.tokenize("0123456789");
        int[] window = new int[context];
        int len = Math.min(encoded.length, context);
        System.arraycopy(encoded, encoded.length - len, window, 0, len);
        int[] outTokens = new int[24];
        int made = 0;
        for (int stepIdx = 0; stepIdx < 24; stepIdx++) {
            int tok = refModel.next(window, len, new Sampler());
            if ((tok < 0) || (tok == saved.tokenizer().end())) {
                break;
            }
            outTokens[made++] = tok;
            if (len < context) {
                window[len++] = tok;
            } else {
                System.arraycopy(window, 1, window, 0, context - 1);
                window[context - 1] = tok;
            }
        }
        String expectedText = core.detokenize(java.util.Arrays.copyOf(outTokens, made));
        assertEquals(expectedText, generated.text);
    }

    @Test
    void parallelTensorAlgebraMatchesSingleThreadedReferenceAndRepeatedBackwardIsClean() {
        int m = 200;
        int k = 32;
        int n = 40;
        Random rng = new Random(42L);
        float[] a = new float[m * k];
        float[] aT = new float[k * m];
        float[] b = new float[k * n];
        float[] bT = new float[n * k];
        for (int i = 0; i < a.length; i++) {
            a[i] = (rng.nextFloat() - 0.5f) * 2f;
        }
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < k; c++) {
                aT[(c * m) + r] = a[(r * k) + c];
            }
        }
        for (int i = 0; i < b.length; i++) {
            b[i] = (rng.nextFloat() - 0.5f) * 2f;
        }
        for (int r = 0; r < k; r++) {
            for (int c = 0; c < n; c++) {
                bT[(c * k) + r] = b[(r * n) + c];
            }
        }

        float[] expected = new float[m * n];
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) {
                float sum = 0f;
                for (int i = 0; i < k; i++) {
                    sum += a[(r * k) + i] * b[(i * n) + c];
                }
                expected[(r * n) + c] = sum;
            }
        }

        float[] abOut = new float[m * n];
        float[] atbOut = new float[m * n];
        float[] abtOut = new float[m * n];
        Tensor.ab(a, m, k, b, n, abOut);
        Tensor.atb(aT, m, k, b, n, atbOut);
        Tensor.abt(a, m, k, bT, n, abtOut);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], abOut[i], 1e-3f);
            assertEquals(expected[i], atbOut[i], 1e-3f);
            assertEquals(expected[i], abtOut[i], 1e-3f);
        }

        // Large Tensor.mv across PART_VALUES boundary
        int vk = 512;
        int vn = 512;
        float[] vec = new float[vk];
        float[] mat = new float[vk * vn];
        float[] mvOut = new float[vn];
        for (int i = 0; i < vk; i++) {
            vec[i] = (rng.nextFloat() - 0.5f);
        }
        for (int i = 0; i < mat.length; i++) {
            mat[i] = (rng.nextFloat() - 0.5f);
        }
        Tensor.mv(vec, vk, mat, vn, mvOut);
        for (int c = 0; c < vn; c++) {
            float ref = 0f;
            for (int r = 0; r < vk; r++) {
                ref += vec[r] * mat[(r * vn) + c];
            }
            assertEquals(ref, mvOut[c], 1e-3f);
        }

        // Repeated backward() without AdamW.step() must yield identical W_TOKEN/W_POSITION grads
        TransformerModel model = new TransformerModel(aModel());
        int[] tokens = { 2, 3, 4, 5, 6, 7 };
        TransformerModel.Step step = model.newStep(tokens.length, true);
        model.forward(tokens, tokens.length, step);
        model.backward(tokens, tokens.length, step);
        float[] firstTokenGrad = model.gradients()[TransformerModel.W_TOKEN].clone();
        float[] firstPosGrad = model.gradients()[TransformerModel.W_POSITION].clone();
        model.forward(tokens, tokens.length, step);
        model.backward(tokens, tokens.length, step);
        for (int i = 0; i < firstTokenGrad.length; i++) {
            assertEquals(firstTokenGrad[i], model.gradients()[TransformerModel.W_TOKEN][i], 0f);
        }
        for (int i = 0; i < firstPosGrad.length; i++) {
            assertEquals(firstPosGrad[i], model.gradients()[TransformerModel.W_POSITION][i], 0f);
        }
    }

    @Test
    void modelConfigAndTransformerRejectInvalidHeadsDivisibility() {
        ModelConfig bad = new ModelConfig();
        bad.setDModel(30); // default heads is 4, does not divide 30
        assertFalse(bad.isValid());
        assertFalse(bad.isTrainable());

        LlmCore core = new LlmCore();
        assertThrows(LlmCoreException.class, () -> core.newModel(bad));
        assertThrows(LlmCoreException.class, () -> new TransformerModel(bad));

        assertTrue(new ModelConfig(null).isValid());
    }

    @Test
    void modelStoreLmc2MetadataCrc32LegacyLmc1AndValidation() throws Exception {
        LlmCore core = new LlmCore();
        core.newModel(aModel());
        core.setLearningRate(0.015d);
        core.setWeightDecay(0.04d);
        core.setLargestGradient(0.8d);
        core.train(List.of("abcdefabcdef"), 6, null, null);
        long expectedSteps = core.steps();
        double expectedLoss = core.loss();

        Path lmc2 = tempDir.resolve("v2.lmc");
        core.save(lmc2);

        ModelStore.Content content = ModelStore.load(lmc2);
        assertTrue(content.isLoaded());
        assertEquals(ModelStore.VERSION, content.formatVersion());

        LlmCore restored = new LlmCore();
        restored.load(lmc2);
        assertEquals(expectedSteps, restored.steps());
        assertEquals(expectedLoss, restored.loss(), 1e-12d);
        assertEquals(0.015d, restored.learningRate(), 1e-12d);
        assertEquals(0.04d, restored.weightDecay(), 1e-12d);
        assertEquals(0.8d, restored.largestGradient(), 1e-12d);

        // Bit-flipped file must fail CRC32 checksum
        byte[] raw = Files.readAllBytes(lmc2);
        raw[raw.length - 12] ^= 0x5A;
        Path flipped = tempDir.resolve("flipped.lmc");
        Files.write(flipped, raw);
        assertFalse(ModelStore.load(flipped).isLoaded());

        // Appended trailing byte must fail
        byte[] orig = Files.readAllBytes(lmc2);
        byte[] padded = java.util.Arrays.copyOf(orig, orig.length + 1);
        padded[padded.length - 1] = 1;
        Path trailing = tempDir.resolve("trailing.lmc");
        Files.write(trailing, padded);
        assertFalse(ModelStore.load(trailing).isLoaded());

        // Non-finite weights are rejected on both save and load
        TransformerModel broken = new TransformerModel(core.config());
        broken.weights()[TransformerModel.W_TOKEN][0] = Float.NaN;
        assertFalse(ModelStore.save(tempDir.resolve("nan.lmc"), core.config(), core.tokenizer(),
                broken.weights()) == null);

        // Legacy LMC1 format remains readable
        Path lmc1 = tempDir.resolve("legacy_v1.lmc");
        TransformerModel legacyModel = new TransformerModel(core.config());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(lmc1))) {
            out.writeInt(ModelStore.MAGIC);
            out.writeInt(ModelStore.VERSION_V1);
            ModelConfig lc = legacyModel.config();
            out.writeInt(lc.vocabType());
            out.writeInt(lc.vocabSize());
            out.writeInt(lc.dModel());
            out.writeInt(lc.heads());
            out.writeInt(lc.layers());
            out.writeInt(lc.context());
            out.writeLong(lc.seed());
            core.tokenizer().writeVocab(out);
            out.writeInt(legacyModel.weights().length);
            for (float[] arr : legacyModel.weights()) {
                out.writeInt(arr.length);
                for (float v : arr) {
                    out.writeFloat(v);
                }
            }
        }
        ModelStore.Content v1Content = ModelStore.load(lmc1);
        assertTrue(v1Content.isLoaded(), () -> "Legacy LMC1 failed: " + v1Content.error());
        assertEquals(ModelStore.VERSION_V1, v1Content.formatVersion());

        // CharTokenizer rejects unpaired surrogate and duplicate code points on readVocab
        ByteArrayOutputStream badSurrogate = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(badSurrogate)) {
            dos.writeInt(1);
            dos.writeInt(0xD800);
        }
        CharTokenizer ct1 = new CharTokenizer();
        assertThrows(IOException.class, () -> ct1.readVocab(
                new DataInputStream(new ByteArrayInputStream(badSurrogate.toByteArray())), 8));

        ByteArrayOutputStream dupCp = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(dupCp)) {
            dos.writeInt(2);
            dos.writeInt('A');
            dos.writeInt('A');
        }
        CharTokenizer ct2 = new CharTokenizer();
        assertThrows(IOException.class, () -> ct2.readVocab(
                new DataInputStream(new ByteArrayInputStream(dupCp.toByteArray())), 8));
    }

    @Test
    void samplerHandlesNanLogitsMinPRepetitionPenaltyAndZeroBoxingPaths() {
        assertEquals(1, Sampler.largest(new float[] { Float.NaN, 0.5f, -2f }));
        assertEquals(1, new Sampler().pick(new float[] { Float.NaN, 0.5f, -2f }));

        // Min-P=0.5 with logits [2.0, 1.5, -10.0]: token 2 ratio exp(-12) << 0.5, never picked
        Sampler minPSampler = new Sampler(1.0d, Sampler.NO_TOP, Sampler.NO_TAIL, 0.5d, 1.0d,
                new Random(123L));
        float[] minPLogits = { 2.0f, 1.5f, -10.0f };
        for (int i = 0; i < 30; i++) {
            int picked = minPSampler.pick(minPLogits);
            assertTrue((picked == 0) || (picked == 1));
        }

        // Repetition penalty strongly penalizes repeated recent token 0
        Sampler repSampler = new Sampler(Sampler.GREEDY, Sampler.NO_TOP, Sampler.NO_TAIL,
                Sampler.NO_MIN_PROBABILITY, 10.0d, new Random(1L));
        int[] recent = { 0 };
        assertEquals(1, repSampler.pick(new float[] { 2.0f, 1.0f, -5.0f }, recent, 1));

        // Deterministic dense & heap-filtered sampling reproducibility
        float[] logits = { 1.2f, 0.9f, 0.3f, -0.5f };
        Sampler denseA = new Sampler(0.8d, 0, 1.0d, new Random(77L));
        Sampler denseB = new Sampler(0.8d, 0, 1.0d, new Random(77L));
        Sampler heapA = new Sampler(0.8d, 2, 0.85d, new Random(77L));
        Sampler heapB = new Sampler(0.8d, 2, 0.85d, new Random(77L));
        for (int i = 0; i < 16; i++) {
            assertEquals(denseA.pick(logits), denseB.pick(logits));
            int h = heapA.pick(logits);
            assertEquals(h, heapB.pick(logits));
            assertTrue((h == 0) || (h == 1));
        }
    }

    @Test
    void statusQueriesRemainNonBlockingDuringTraining() throws Exception {
        LlmCore core = new LlmCore();
        core.newModel(aModel());
        core.setLearningRate(0.02d);

        CompletableFuture<LlmCore.TrainResult> future = CompletableFuture.supplyAsync(
                () -> core.train(List.of("abcdefghabcdefgh"), LlmCore.MAX_EPOCHS, null, null));

        long waitStart = System.currentTimeMillis();
        while ((core.state() != LlmCore.TRAINING) && !future.isDone()) {
            if ((System.currentTimeMillis() - waitStart) > 5000L) {
                core.stop();
                throw new AssertionError("Training never transitioned to TRAINING state");
            }
            Thread.sleep(5L);
        }

        try {
            long queryStart = System.currentTimeMillis();
            assertEquals(0.02d, core.learningRate(), 1e-12d);
            assertTrue(Double.isFinite(core.weightDecay()));
            assertTrue(Double.isFinite(core.largestGradient()));
            assertEquals(1, core.texts());
            assertTrue(core.tokens() > 0L);
            assertTrue(core.parameters() > 0L);
            long queryElapsed = System.currentTimeMillis() - queryStart;
            assertTrue(queryElapsed < 1000L, "Status query blocked for " + queryElapsed + " ms");
        } finally {
            core.stop();
            LlmCore.TrainResult res = future.get(10L, TimeUnit.SECONDS);
            assertTrue(res.stopped);
        }
    }

    @Test
    void tensorExtremesSoftmaxEdgeCasesAndTiledKernelsAreExact() {
        float[] geluInputs = { -Float.MAX_VALUE, -1e20f, -10f, -2f, 0f, 2f, 10f, 1e20f,
                Float.MAX_VALUE, Float.NaN };
        float[] geluDeriv = new float[geluInputs.length];
        Tensor.geluDerivative(geluInputs, geluDeriv);
        assertEquals(0f, geluDeriv[0], 0f);
        assertEquals(0f, geluDeriv[1], 0f);
        assertEquals(0f, geluDeriv[2], 0f);
        assertTrue(Float.isFinite(geluDeriv[3]) && (geluDeriv[3] < 0f));
        assertEquals(0.5f, geluDeriv[4], 1e-6f);
        assertEquals(1f, geluDeriv[6], 0f);
        assertEquals(1f, geluDeriv[7], 0f);
        assertEquals(1f, geluDeriv[8], 0f);
        assertTrue(Float.isNaN(geluDeriv[9]));

        assertEquals(1e20f, Tensor.geluValue(1e20f), 0f);
        assertEquals(0f, Tensor.geluValue(-1e20f), 0f);
        assertTrue(Float.isNaN(Tensor.geluValue(Float.NaN)));

        // Softmax: leading NaN poisons uniformly to NaN instead of partial normalization
        float[] nanRow = { Float.NaN, 1f, 2f };
        Tensor.softmax(nanRow, 0, nanRow.length);
        for (float v : nanRow) {
            assertTrue(Float.isNaN(v));
        }

        // Softmax: multiple +Infinity split probability mass equally; -Infinity gets 0
        float[] posInfRow = { Float.POSITIVE_INFINITY, 3f, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY };
        Tensor.softmax(posInfRow, 0, posInfRow.length);
        assertEquals(0.5f, posInfRow[0], 1e-6f);
        assertEquals(0f, posInfRow[1], 0f);
        assertEquals(0.5f, posInfRow[2], 1e-6f);
        assertEquals(0f, posInfRow[3], 0f);

        // Softmax: all -Infinity falls back cleanly to uniform
        float[] negInfRow = { Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY };
        Tensor.softmax(negInfRow, 0, negInfRow.length);
        for (float v : negInfRow) {
            assertEquals(1f / 3f, v, 1e-6f);
        }

        // Odd/large tiled algebra (> PART_VALUES) + atbAdd accumulation
        int m = 133;
        int k = 45;
        int n = 99;
        Random rng = new Random(99L);
        float[] a = new float[m * k];
        float[] aT = new float[k * m];
        float[] b = new float[k * n];
        float[] bT = new float[n * k];
        for (int i = 0; i < a.length; i++) {
            a[i] = (rng.nextFloat() - 0.5f);
        }
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < k; c++) {
                aT[(c * m) + r] = a[(r * k) + c];
            }
        }
        for (int i = 0; i < b.length; i++) {
            b[i] = (rng.nextFloat() - 0.5f);
        }
        for (int r = 0; r < k; r++) {
            for (int c = 0; c < n; c++) {
                bT[(c * k) + r] = b[(r * n) + c];
            }
        }
        float[] abOut = new float[m * n];
        float[] atbOut = new float[m * n];
        float[] atbAcc = new float[m * n];
        java.util.Arrays.fill(atbAcc, 1.5f);
        float[] abtOut = new float[m * n];
        Tensor.ab(a, m, k, b, n, abOut);
        Tensor.atb(aT, m, k, b, n, atbOut);
        Tensor.atbAdd(aT, m, k, b, n, atbAcc);
        Tensor.abt(a, m, k, bT, n, abtOut);
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < n; c++) {
                float ref = 0f;
                for (int i = 0; i < k; i++) {
                    ref += a[(r * k) + i] * b[(i * n) + c];
                }
                int idx = (r * n) + c;
                assertEquals(ref, abOut[idx], 1e-3f);
                assertEquals(ref, atbOut[idx], 1e-3f);
                assertEquals(ref + 1.5f, atbAcc[idx], 1e-3f);
                assertEquals(ref, abtOut[idx], 1e-3f);
            }
        }
    }

    @Test
    void transformerPrefillStepReuseThreadSafetyAccumulationAndConfigNormalization()
            throws Exception {
        ModelConfig rawCfg = aModel();
        rawCfg.setContext(16);
        TransformerModel model = new TransformerModel(rawCfg);
        assertEquals(model.vocabulary(), model.config().vocabSize());

        for (float[] w : model.weights()) {
            for (int i = 0; i < w.length; i++) {
                w[i] *= 3f;
            }
        }
        int[] seq = { 2, 3, 4, 5, 6, 7, 2, 5, 3, 7, 4, 6 };

        // Batched prefill matches full forward logits and supports subsequent incremental steps
        TransformerModel.KvCache cache = model.newCache();
        float[] prefillLogits = model.prefill(seq, 8, cache);
        float[] fullLogits8 = model.logits(seq, 8);
        assertEquals(8, cache.length());
        for (int j = 0; j < fullLogits8.length; j++) {
            assertEquals(fullLogits8[j], prefillLogits[j], 1e-4f);
        }
        float[] step9 = model.step(seq[8], 8, cache);
        float[] fullLogits9 = model.logits(seq, 9);
        for (int j = 0; j < fullLogits9.length; j++) {
            assertEquals(fullLogits9[j], step9[j], 1e-4f);
        }

        // Concurrent logits() & loss() must remain completely thread-safe across varying lengths
        int[] seqA = { 2, 3, 4, 5, 6, 7 };
        int[] seqB = { 7, 6, 5, 4, 3, 2, 5, 4, 3, 2, 7, 6 };
        float[] refLogitsA = model.logits(seqA, seqA.length);
        float[] refLogitsB = model.logits(seqB, seqB.length);
        double refLossA = model.loss(seqA, seqA.length);
        double refLossB = model.loss(seqB, seqB.length);

        List<CompletableFuture<Void>> workers = new java.util.ArrayList<>();
        for (int t = 0; t < 4; t++) {
            final boolean useA = (t % 2) == 0;
            workers.add(CompletableFuture.runAsync(() -> {
                for (int iter = 0; iter < 20; iter++) {
                    if (useA) {
                        float[] out = model.logits(seqA, seqA.length);
                        for (int j = 0; j < out.length; j++) {
                            assertEquals(refLogitsA[j], out[j], 0f);
                        }
                        assertEquals(refLossA, model.loss(seqA, seqA.length), 0d);
                    } else {
                        float[] out = model.logits(seqB, seqB.length);
                        for (int j = 0; j < out.length; j++) {
                            assertEquals(refLogitsB[j], out[j], 0f);
                        }
                        assertEquals(refLossB, model.loss(seqB, seqB.length), 0d);
                    }
                }
            }));
        }
        CompletableFuture.allOf(workers.toArray(CompletableFuture[]::new)).get(10L, TimeUnit.SECONDS);

        // Variable-length Step capacity reuse must match fresh-allocated Step exactly
        int[] lengths = { 6, 12, 4, 16, 8 };
        int[] longTokens = { 2, 3, 4, 5, 6, 7, 2, 4, 6, 3, 5, 7, 2, 5, 3, 7 };
        for (int len : lengths) {
            TransformerModel.Step shared = model.newStep(len, true);
            model.forward(longTokens, len, shared);
            double sharedLoss = model.backward(longTokens, len, shared);
            float[] sharedHeadGrad = model.gradients()[TransformerModel.W_HEAD].clone();

            TransformerModel freshCopy = new TransformerModel(model.config());
            for (int leaf = 0; leaf < freshCopy.weights().length; leaf++) {
                System.arraycopy(model.weights()[leaf], 0, freshCopy.weights()[leaf], 0,
                        model.weights()[leaf].length);
            }
            TransformerModel.Step exact = freshCopy.newStep(len, true);
            freshCopy.forward(longTokens, len, exact);
            double exactLoss = freshCopy.backward(longTokens, len, exact);
            assertEquals(exactLoss, sharedLoss, 1e-12d);
            for (int i = 0; i < sharedHeadGrad.length; i++) {
                assertEquals(freshCopy.gradients()[TransformerModel.W_HEAD][i],
                        sharedHeadGrad[i], 0f);
            }
        }

        // Accumulated backward across two windows equals sum of individual backward gradients
        int[] w1 = { 2, 3, 4, 5, 6 };
        int[] w2 = { 7, 5, 4, 3, 2, 6, 3 };
        TransformerModel copy1 = new TransformerModel(model.config());
        TransformerModel copy2 = new TransformerModel(model.config());
        for (int leaf = 0; leaf < model.weights().length; leaf++) {
            System.arraycopy(model.weights()[leaf], 0, copy1.weights()[leaf], 0,
                    model.weights()[leaf].length);
            System.arraycopy(model.weights()[leaf], 0, copy2.weights()[leaf], 0,
                    model.weights()[leaf].length);
        }
        TransformerModel.Step s1 = copy1.newStep(w1.length, true);
        copy1.forward(w1, w1.length, s1);
        copy1.backward(w1, w1.length, s1);

        TransformerModel.Step s2 = copy2.newStep(w2.length, true);
        copy2.forward(w2, w2.length, s2);
        copy2.backward(w2, w2.length, s2);

        TransformerModel.Step accS1 = model.newStep(w1.length, true);
        model.forward(w1, w1.length, accS1);
        model.backward(w1, w1.length, accS1, false);
        TransformerModel.Step accS2 = model.newStep(w2.length, true);
        model.forward(w2, w2.length, accS2);
        model.backward(w2, w2.length, accS2, true);

        for (int leaf = 0; leaf < model.gradients().length; leaf++) {
            for (int i = 0; i < model.gradients()[leaf].length; i++) {
                double expected = (double) copy1.gradients()[leaf][i] + copy2.gradients()[leaf][i];
                assertEquals(expected, model.gradients()[leaf][i], 1e-4d,
                        "Accumulated grad mismatch at leaf=" + leaf + " idx=" + i);
            }
        }

        // Backward length < 2 throws cleanly instead of dividing by zero
        TransformerModel.Step shortStep = model.newStep(2, true);
        assertThrows(LlmCoreException.class, () -> model.backward(new int[] { 2 }, 1, shortStep));
    }

    @Test
    void adamWElementParallelLargeLeafAndScheduledScalingMatchScalarReference() {
        int bigSize = (int) Tensor.PART_VALUES + 512;
        float[] wBig = new float[bigSize];
        float[] gBig = new float[bigSize];
        float[] wSmall = { 0.5f, -0.25f };
        float[] gSmall = { 0.1f, -0.3f };
        Random rng = new Random(11L);
        for (int i = 0; i < bigSize; i++) {
            wBig[i] = (rng.nextFloat() - 0.5f);
            gBig[i] = (rng.nextFloat() - 0.5f) * 0.2f;
        }
        float[] refWBig = wBig.clone();
        float[] refGBig = gBig.clone();
        float[] refWSmall = wSmall.clone();
        float[] refGSmall = gSmall.clone();

        AdamW opt = new AdamW(new float[][] { wBig, wSmall }, new boolean[] { true, false });
        opt.setLearningRate(0.02d);
        opt.setWeightDecay(0.05d);
        opt.setLargestGradient(0d);

        double effectiveLr = 0.01d;
        double gradScale = 0.5d;
        opt.step(new float[][] { wBig, wSmall }, new float[][] { gBig, gSmall }, effectiveLr,
                gradScale);
        // Caller-configured peak LR remains unchanged by scheduled step override
        assertEquals(0.02d, opt.learningRate(), 0d);
        assertEquals(1, opt.steps());

        double stepSize = effectiveLr / (1d - 0.9d);
        double invSecond = 1d / (1d - 0.999d);
        double decay = effectiveLr * 0.05d;
        for (int i = 0; i < bigSize; i++) {
            double g = refGBig[i] * gradScale;
            double m = 0.1d * g;
            double v = 0.001d * g * g;
            double moved = (m * stepSize) / (Math.sqrt(v * invSecond) + 1e-8d);
            float expected = (float) (refWBig[i] - (decay * refWBig[i]) - moved);
            assertEquals(expected, wBig[i], 1e-6f);
            assertEquals(0f, gBig[i], 0f);
        }
        for (int i = 0; i < wSmall.length; i++) {
            double g = refGSmall[i] * gradScale;
            double m = 0.1d * g;
            double v = 0.001d * g * g;
            double moved = (m * stepSize) / (Math.sqrt(v * invSecond) + 1e-8d);
            float expected = (float) (refWSmall[i] - moved);
            assertEquals(expected, wSmall[i], 1e-6f);
            assertEquals(0f, gSmall[i], 0f);
        }

        // Constructor and step validation
        assertThrows(LlmCoreException.class, () -> new AdamW(null));
        assertThrows(LlmCoreException.class, () -> new AdamW(new float[][] { { 1f } },
                new boolean[] { true, false }));
        assertThrows(LlmCoreException.class, () -> opt.step(new float[][] { wBig },
                new float[][] { gBig, gSmall }, 0.01d, 1d));
        assertThrows(LlmCoreException.class, () -> opt.step(new float[][] { wBig, wSmall },
                new float[][] { gBig, gSmall }, -0.01d, 1d));
        assertThrows(LlmCoreException.class, () -> opt.step(new float[][] { wBig, wSmall },
                new float[][] { gBig, gSmall }, 0.01d, 0d));
    }

    @Test
    void samplerInfinityConsistencyTopOneFastPathAndTokenizerDatasetModelStoreValidation()
            throws Exception {
        Sampler warm = new Sampler(1.0d, Sampler.NO_TOP, Sampler.NO_TAIL, new Random(5L));
        float[] oneInf = { 2f, Float.POSITIVE_INFINITY, 100f };
        assertEquals(1, Sampler.largest(oneInf));
        assertEquals(1, warm.pick(oneInf));

        warm.setTemperature(Double.POSITIVE_INFINITY);
        assertEquals(0d, warm.temperature(), 0d);

        Sampler top1 = new Sampler(2.0d, 1, 0.5d, 0.2d, 1.0d, new Random(9L));
        float[] ranked = { -1f, 3.5f, 2.0f, 0.5f };
        for (int i = 0; i < 10; i++) {
            assertEquals(1, top1.pick(ranked));
        }

        // CharTokenizer rejects/skips unpaired surrogates so save/load/retrain never corrupts
        LlmCore surrogateCore = new LlmCore();
        surrogateCore.newModel(aModel());
        surrogateCore.train(List.of("ab\uD800cd", "abcdab"), 2, 0.02d, null);
        Path surrFile = tempDir.resolve("surrogate.lmc");
        surrogateCore.save(surrFile);
        LlmCore reloaded = new LlmCore();
        reloaded.load(surrFile);
        assertEquals(surrogateCore.tokenizer().size(), reloaded.tokenizer().size());
        reloaded.train(List.of("ab\uD800cd"), 1, 0.02d, null);

        // Dataset guards
        assertThrows(LlmCoreException.class, () -> new Dataset(null));
        Dataset ds = new Dataset(new ByteTokenizer());
        ds.add("abc");
        assertThrows(LlmCoreException.class, () -> ds.windows(0, 1));
        assertEquals(null, ds.sample(new Random(1L), 1));

        // ModelConfig equals/hashCode/toString
        ModelConfig c1 = aModel();
        ModelConfig c2 = new ModelConfig(c1);
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        c2.setSeed(99L);
        assertNotEquals(c1, c2);
        assertTrue(c1.toString().contains("vocabType=char"));

        // ModelStore save mismatch & corrupt LMC2 metadata checks
        TransformerModel charModel = new TransformerModel(aModel());
        assertFalse(ModelStore.save(tempDir.resolve("bad_tok.lmc"), charModel.config(),
                new ByteTokenizer(), charModel.weights()) == null);
        ModelConfig zeroChar = new ModelConfig(charModel.config());
        zeroChar.setVocabSize(0);
        CharTokenizer realCharTok = new CharTokenizer();
        realCharTok.learn("abc");
        assertFalse(ModelStore.save(tempDir.resolve("small_vocab.lmc"), zeroChar,
                realCharTok, charModel.weights()) == null);
        assertFalse(ModelStore.save(tempDir.resolve("neg_loss.lmc"), charModel.config(),
                new CharTokenizer(), charModel.weights(),
                new ModelStore.Metadata(1L, -0.5d, 0.01d, 0.01d, 1d)) == null);

        // ModelStore.load rejects corrupt LMC2 metadata even with a valid CRC32 checksum
        Path badMetaFile = tempDir.resolve("bad_meta_v2.lmc");
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        try (java.io.BufferedOutputStream raw = new java.io.BufferedOutputStream(
                Files.newOutputStream(badMetaFile))) {
            java.util.zip.CheckedOutputStream checked =
                    new java.util.zip.CheckedOutputStream(raw, crc);
            DataOutputStream dos = new DataOutputStream(checked);
            ModelConfig mc = charModel.config();
            dos.writeInt(ModelStore.MAGIC);
            dos.writeInt(ModelStore.VERSION);
            dos.writeInt(mc.vocabType());
            dos.writeInt(mc.vocabSize());
            dos.writeInt(mc.dModel());
            dos.writeInt(mc.heads());
            dos.writeInt(mc.layers());
            dos.writeInt(mc.context());
            dos.writeLong(mc.seed());
            dos.writeLong(1L);
            dos.writeDouble(-1.0d); // invalid negative loss
            dos.writeDouble(0.01d);
            dos.writeDouble(0.01d);
            dos.writeDouble(1.0d);
            new CharTokenizer().writeVocab(dos);
            dos.writeInt(charModel.weights().length);
            for (float[] arr : charModel.weights()) {
                dos.writeInt(arr.length);
                for (float v : arr) {
                    dos.writeFloat(v);
                }
            }
            dos.flush();
            DataOutputStream trailer = new DataOutputStream(raw);
            trailer.writeLong(crc.getValue());
            trailer.flush();
        }
        assertFalse(ModelStore.load(badMetaFile).isLoaded());
    }

    @Test
    void trainOptionsEvaluateStopAndNonDuplicateProgressWork() throws Exception {
        // 1-window training (planned == 1) must fire onProgress once per epoch with unique steps
        LlmCore singleWinCore = new LlmCore();
        singleWinCore.newModel(aModel());
        java.util.Set<Long> uniqueSteps = new java.util.HashSet<>();
        java.util.concurrent.atomic.AtomicInteger singleProgressCount =
                new java.util.concurrent.atomic.AtomicInteger();
        LlmCore.TrainResult singleRes = singleWinCore.train(
                List.of("abcdefgh"),
                5,
                0.02d,
                new LlmCoreListener() {
                    @Override
                    public void onProgress(int epoch, int epochs, long steps, double loss,
                            double seconds) {
                        singleProgressCount.incrementAndGet();
                        assertTrue(uniqueSteps.add(steps),
                                "Duplicate progress step detected: " + steps);
                    }
                });
        assertEquals(5, singleRes.epochs);
        assertEquals(5, singleProgressCount.get());
        assertEquals(5L, singleRes.steps);

        LlmCore core = new LlmCore();
        core.newModel(aModel());
        core.setLearningRate(0.03d);

        LlmCore.TrainOptions options = new LlmCore.TrainOptions(15, 0.03d, 0.005d, 3, 2);
        LlmCore.TrainResult result = core.train(
                List.of("abcdefgh", "efghabcd", "cdabefgh"),
                options,
                null);
        assertFalse(result.stopped);
        assertEquals(core.steps(), result.steps);
        assertTrue(result.steps > 0L);
        assertEquals(0.03d, core.learningRate(), 0d);
        assertTrue(result.loss < 1.5d);

        // Pre-requested stop during evaluate returns stopped == true cleanly
        core.stop();
        LlmCore.EvalResult earlyStop = core.evaluate(List.of("abcdefgh"));
        assertTrue(earlyStop.stopped);
        assertEquals(0L, earlyStop.tokens);
        assertFalse(core.evaluate(List.of("abcdefgh")).stopped);
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


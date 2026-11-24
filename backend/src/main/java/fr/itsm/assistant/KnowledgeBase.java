package fr.itsm.assistant;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class KnowledgeBase {
    private static final double MIN_SIMILARITY = 0.4;
    private static final int MAX_SOURCES = 3;

    private final OllamaClient ollama;
    private final List<Source> sources = new ArrayList<>();
    private List<double[]> sourceVectors;

    public KnowledgeBase(OllamaClient ollama, ResourcePatternResolver resources) throws IOException {
        this.ollama = ollama;
        Resource[] files = resources.getResources("classpath*:kb/*.md");
        Arrays.sort(files, Comparator.comparing(Resource::getFilename));
        for (Resource file : files) {
            String text = file.getContentAsString(StandardCharsets.UTF_8).strip();
            String title = text.lines().findFirst().orElse("").replaceFirst("^#\\s+", "");
            // Chaque paragraphe indépendant devient un extrait qui peut être cité.
            for (String paragraph : text.split("\\R\\s*\\R")) {
                if (!paragraph.startsWith("# ") && !paragraph.isBlank()) {
                    sources.add(new Source("S" + (sources.size() + 1), title,
                            file.getFilename(), paragraph.strip()));
                }
            }
        }
    }

    public List<Source> search(String question) {
        if (sources.isEmpty()) {
            return List.of();
        }
        List<double[]> vectors = getSourceVectors();
        double[] queryVector = ollama.embed(List.of(question)).getFirst();
        var matches = new ArrayList<Match>();
        for (int i = 0; i < sources.size(); i++) {
            double score = cosine(queryVector, vectors.get(i));
            if (score >= MIN_SIMILARITY) {
                matches.add(new Match(sources.get(i), score));
            }
        }

        matches.sort(Comparator.comparingDouble(Match::score).reversed());
        List<Source> results = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_SOURCES, matches.size()); i++) {
            results.add(matches.get(i).source());
        }
        return results;
    }

    private synchronized List<double[]> getSourceVectors() {
        if (sourceVectors == null) {
            List<String> paragraphs = new ArrayList<>();
            for (Source source : sources) {
                paragraphs.add(source.title() + "\n" + source.excerpt());
            }
            sourceVectors = ollama.embed(paragraphs);
        }
        return sourceVectors;
    }

    private static double cosine(double[] left, double[] right) {
        if (left.length != right.length) {
            throw new RestClientException(
                    "Embedding dimensions changed. Restart the backend after changing models.");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    public record Source(String id, String title, String file, String excerpt) {}
    private record Match(Source source, double score) {}
}

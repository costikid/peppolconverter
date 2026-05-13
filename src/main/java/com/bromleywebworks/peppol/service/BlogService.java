package com.bromleywebworks.peppol.service;

import com.bromleywebworks.peppol.dto.BlogPost;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BlogService {

    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public List<BlogPost> getAllPosts() {
        List<BlogPost> posts = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources("classpath:blog/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;
                String slug = filename.replace(".md", "");
                String raw = readResource(resource);
                BlogPost post = parsePost(slug, raw);
                if (post != null) {
                    posts.add(post);
                }
            }
        } catch (Exception e) {
            log.warn("Could not load blog posts: {}", e.getMessage());
        }
        return posts.stream()
                .sorted(Comparator.comparing(BlogPost::getDate).reversed())
                .collect(Collectors.toList());
    }

    public Optional<BlogPost> getPost(String slug) {
        try {
            Resource resource = resolver.getResource("classpath:blog/" + slug + ".md");
            if (!resource.exists()) {
                return Optional.empty();
            }
            String raw = readResource(resource);
            return Optional.ofNullable(parsePost(slug, raw));
        } catch (Exception e) {
            log.warn("Could not load blog post '{}': {}", slug, e.getMessage());
            return Optional.empty();
        }
    }

    private BlogPost parsePost(String slug, String raw) {
        try {
            Map<String, String> frontMatter = new HashMap<>();
            String body;

            if (raw.startsWith("---")) {
                int end = raw.indexOf("---", 3);
                if (end == -1) {
                    body = raw;
                } else {
                    String fmBlock = raw.substring(3, end).trim();
                    body = raw.substring(end + 3).trim();
                    for (String line : fmBlock.split("\n")) {
                        int colon = line.indexOf(':');
                        if (colon > 0) {
                            String key = line.substring(0, colon).trim();
                            String value = line.substring(colon + 1).trim();
                            frontMatter.put(key, value);
                        }
                    }
                }
            } else {
                body = raw;
            }

            String title = frontMatter.getOrDefault("title", slug);
            String summary = frontMatter.getOrDefault("summary", "");
            LocalDate date = frontMatter.containsKey("date")
                    ? LocalDate.parse(frontMatter.get("date"))
                    : LocalDate.now();

            String htmlContent = htmlRenderer.render(markdownParser.parse(body));

            return BlogPost.builder()
                    .slug(slug)
                    .title(title)
                    .date(date)
                    .summary(summary)
                    .htmlContent(htmlContent)
                    .build();

        } catch (Exception e) {
            log.warn("Could not parse blog post '{}': {}", slug, e.getMessage());
            return null;
        }
    }

    private String readResource(Resource resource) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}

package com.example.feuerwerkagent.service;

import com.example.feuerwerkagent.config.SearchConfig;
import com.example.feuerwerkagent.dto.SearchRequest;
import com.example.feuerwerkagent.dto.SearchResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class SearchService {

    private final SearchConfig searchConfig;
    private final ChatService chatService;
    private final OkHttpClient httpClient;

    public SearchService(SearchConfig searchConfig, ChatService chatService) {
        this.searchConfig = searchConfig;
        this.chatService = chatService;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)  // 增加连接超时
                .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)    // 增加读取超时
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public SearchResponse search(SearchRequest searchRequest) {
        try {
            List<SearchResponse.SearchResult> searchResults = performWebSearch(searchRequest.getQuery());

            String aiSummary = null;
            if (searchRequest.isEnableAI() && !searchResults.isEmpty()) {
                aiSummary = generateAISummary(searchRequest.getQuery(), searchResults);
            }

            return SearchResponse.success(searchRequest.getQuery(), searchResults, aiSummary);

        } catch (Exception e) {
            return SearchResponse.error("Error performing search: " + e.getMessage());
        }
    }

    public SearchResponse simpleSearch(String query) {
        SearchRequest request = new SearchRequest();
        request.setQuery(query);
        request.setMaxResults(10);
        request.setEnableAI(true);
        return search(request);
    }

    private List<SearchResponse.SearchResult> performWebSearch(String query) throws Exception {
        List<SearchResponse.SearchResult> results = new ArrayList<>();

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name());

            // 尝试多个国内搜索引擎
            String[] searchEngines = {
                    "https://www.baidu.com/s?wd=" + encodedQuery + "&rn=10",  // 百度
                    "https://www.so.com/s?q=" + encodedQuery + "&pn=1",       // 360搜索
                    "https://www.sogou.com/web?query=" + encodedQuery + "&page=1" // 搜狗
            };

            for (int i = 0; i < searchEngines.length && results.isEmpty(); i++) {
                try {
                    String searchUrl = searchEngines[i];
                    String engineName = getEngineName(i);

                    Request request = new Request.Builder()
                            .url(searchUrl)
                            .addHeader("User-Agent", searchConfig.getUserAgent())
                            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                            .get()
                            .build();

                    try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            String html = response.body().string();
                            results.addAll(parseHtmlSearchResults(html, engineName));

                            if (!results.isEmpty()) {
                                System.out.println("使用 " + engineName + " 搜索成功，找到 " + results.size() + " 条结果");
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("搜索引擎 " + i + " 失败: " + e.getMessage());
                }
            }

            // 如果所有搜索引擎都失败，返回模拟结果
            if (results.isEmpty()) {
                results.add(createFallbackResult(query));
            }

        } catch (Exception e) {
            throw new RuntimeException("Error encoding query", e);
        }

        return results;
    }

    private String getEngineName(int index) {
        String[] names = {"百度", "360搜索", "搜狗"};
        return names[index];
    }

    private List<SearchResponse.SearchResult> parseHtmlSearchResults(String html, String engineName) {
        List<SearchResponse.SearchResult> results = new ArrayList<>();

        try {
            if (engineName.equals("百度")) {
                results.addAll(parseBaiduResults(html));
            } else if (engineName.equals("360搜索")) {
                results.addAll(parseSoResults(html));
            } else if (engineName.equals("搜狗")) {
                results.addAll(parseSogouResults(html));
            }
        } catch (Exception e) {
            System.err.println("解析 " + engineName + " 结果失败: " + e.getMessage());
        }

        return results;
    }

    private List<SearchResponse.SearchResult> parseBaiduResults(String html) {
        List<SearchResponse.SearchResult> results = new ArrayList<>();

        try {
            // 百度搜索结果解析
            String[] patterns = {
                    "<div class=\"result c-container \".*?<h3.*?<a.*?href=\"([^\"]+)\".*?>(.*?)</a>.*?<p class=\"content_.*?\"*?>(.*?)</p>",
                    "<div class=\"c-container\".*?<h3.*?<a.*?href=\"([^\"]+)\".*?>(.*?)</a>.*?<p class=\"c-abstract\"*?>(.*?)</p>",
                    "<div class=\"result\".*?<h3.*?<a.*?href=\"([^\"]+)\".*?>(.*?)</a>.*?class=\"c-abstract\"*?>(.*?)</p>"
            };

            for (String pattern : patterns) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher m = p.matcher(html);

                while (m.find() && results.size() < 10) {
                    SearchResponse.SearchResult result = new SearchResponse.SearchResult();
                    String url = m.group(1);

                    // 清理URL和标题
                    url = url.replaceAll("&amp;", "&");
                    url = cleanBaiduUrl(url);

                    String title = m.group(2).replaceAll("<[^>]*>", "").trim();
                    String snippet = m.group(3).replaceAll("<[^>]*>", "").trim();

                    if (title.length() > 100) title = title.substring(0, 100);
                    if (snippet.length() > 200) snippet = snippet.substring(0, 200);

                    result.setUrl(url);
                    result.setTitle(title);
                    result.setSnippet(snippet);
                    result.setSource("百度");
                    results.add(result);
                }

                if (!results.isEmpty()) break;
            }
        } catch (Exception e) {
            System.err.println("百度解析失败: " + e.getMessage());
        }

        return results;
    }

    private List<SearchResponse.SearchResult> parseSoResults(String html) {
        List<SearchResponse.SearchResult> results = new ArrayList<>();

        try {
            // 360搜索结果解析
            String pattern = "<li class=\"res-list\".*?<h3.*?<a.*?href=\"([^\"]+)\".*?>(.*?)</a>.*?class=\"desc\"*?>(.*?)</p>";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(html);

            while (m.find() && results.size() < 10) {
                SearchResponse.SearchResult result = new SearchResponse.SearchResult();
                String url = m.group(1).replaceAll("&amp;", "&");
                String title = m.group(2).replaceAll("<[^>]*>", "").trim();
                String snippet = m.group(3).replaceAll("<[^>]*>", "").trim();

                if (title.length() > 100) title = title.substring(0, 100);
                if (snippet.length() > 200) snippet = snippet.substring(0, 200);

                result.setUrl(url);
                result.setTitle(title);
                result.setSnippet(snippet);
                result.setSource("360搜索");
                results.add(result);
            }
        } catch (Exception e) {
            System.err.println("360搜索解析失败: " + e.getMessage());
        }

        return results;
    }

    private List<SearchResponse.SearchResult> parseSogouResults(String html) {
        List<SearchResponse.SearchResult> results = new ArrayList<>();

        try {
            // 搜狗搜索结果解析
            String pattern = "<div class=\"rb\".*?<h3.*?<a.*?href=\"([^\"]+)\".*?>(.*?)</a>.*?class=\"ft\"*?>(.*?)</p>";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(html);

            while (m.find() && results.size() < 10) {
                SearchResponse.SearchResult result = new SearchResponse.SearchResult();
                String url = m.group(1).replaceAll("&amp;", "&");
                String title = m.group(2).replaceAll("<[^>]*>", "").trim();
                String snippet = m.group(3).replaceAll("<[^>]*>", "").trim();

                if (title.length() > 100) title = title.substring(0, 100);
                if (snippet.length() > 200) snippet = snippet.substring(0, 200);

                result.setUrl(url);
                result.setTitle(title);
                result.setSnippet(snippet);
                result.setSource("搜狗");
                results.add(result);
            }
        } catch (Exception e) {
            System.err.println("搜狗解析失败: " + e.getMessage());
        }

        return results;
    }

    private String cleanBaiduUrl(String url) {
        // 清理百度URL中的重定向参数
        if (url.contains("baidu.com/link")) {
            // 从百度重定向URL中提取真实URL
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("url=([^&]+)");
            java.util.regex.Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                try {
                    return java.net.URLDecoder.decode(matcher.group(1), "UTF-8");
                } catch (Exception e) {
                    // 解码失败，返回原URL
                }
            }
        }
        return url;
    }

    private SearchResponse.SearchResult createFallbackResult(String query) {
        SearchResponse.SearchResult result = new SearchResponse.SearchResult();
        result.setTitle("搜索建议 - " + query);
        result.setUrl("https://www.baidu.com/s?wd=" + URLEncoder.encode(query));
        result.setSnippet("由于网络原因，无法完成搜索。您可以点击这里手动搜索：" + query);
        result.setSource("系统建议");
        return result;
    }


    private String generateAISummary(String query, List<SearchResponse.SearchResult> results) {
        try {
            StringBuilder searchContext = new StringBuilder();
            searchContext.append("Search Query: ").append(query).append("\n\n");
            searchContext.append("Search Results:\n");

            for (int i = 0; i < Math.min(5, results.size()); i++) {
                SearchResponse.SearchResult result = results.get(i);
                searchContext.append(String.format("%d. %s\n   URL: %s\n   Snippet: %s\n\n",
                        i + 1, result.getTitle(), result.getUrl(), result.getSnippet()));
            }

            String summaryPrompt = String.format(
                    "Based on the following search results, please provide a comprehensive and accurate summary. " +
                            "Focus on directly answering the search query using the information from the results. " +
                            "If the results don't contain relevant information, please state that clearly.\n\n%s",
                    searchContext.toString()
            );

            var chatResponse = chatService.simpleChat(summaryPrompt);

            if (chatResponse.isSuccess()) {
                return chatResponse.getContent();
            } else {
                return "Unable to generate AI summary: " + chatResponse.getError();
            }

        } catch (Exception e) {
            return "Error generating AI summary: " + e.getMessage();
        }
    }
}

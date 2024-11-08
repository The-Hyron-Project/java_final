package searchengine.dto.statistics;

import lombok.Getter;

@Getter
public class SearchResponseItem {
  String site;
  String siteName;
  String uri;
  String title;
  String snippet;
  Double relevance;

  public SearchResponseItem(String site, String siteName, String uri, String title, String snippet,
      Double relevance) {
    this.site = site;
    this.siteName = siteName;
    this.uri = uri;
    this.title = title;
    this.snippet = snippet;
    this.relevance = relevance;
  }
}

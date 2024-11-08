package searchengine.dto.statistics;

import java.util.ArrayList;
import lombok.Getter;

@Getter
public class SearchResponseSucceeded extends RequestResponse{
  int count;
  ArrayList<SearchResponseItem> data;

  public SearchResponseSucceeded(Boolean result, int count, ArrayList<SearchResponseItem> data) {
    super(result);
    this.count=count;
    this.data=data;
  }
}

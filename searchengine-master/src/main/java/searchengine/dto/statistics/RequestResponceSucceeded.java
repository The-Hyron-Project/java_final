package searchengine.dto.statistics;

import lombok.Getter;

@Getter
public class RequestResponceSucceeded extends RequestResponse{

  public RequestResponceSucceeded(Boolean result) {
    super(result);
  }
}

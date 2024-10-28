package searchengine.dto.statistics;

import lombok.Getter;

@Getter
public class RequestResponceFailed extends RequestResponse {
    String error;


  public RequestResponceFailed(Boolean result, String error) {
    super(result);
    this.error=error;
  }

  public String getError() {
    return error;
  }
}

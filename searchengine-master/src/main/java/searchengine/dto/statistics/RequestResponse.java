package searchengine.dto.statistics;

import lombok.Getter;

@Getter
public abstract class RequestResponse {
  Boolean result;

  public RequestResponse(Boolean result){
    this.result=result;
  }
}

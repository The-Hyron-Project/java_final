package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.RequestResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.PageIndexingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final PageIndexingService pageIndexingService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService,
        PageIndexingService pageIndexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.pageIndexingService = pageIndexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<RequestResponse>  startIndexing() {
        return ResponseEntity.ok(indexingService.getIndexResult());

    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<RequestResponse> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<RequestResponse> indexPage(@RequestParam String url)  {
        return ResponseEntity.ok(pageIndexingService.startPageIndexing(url));
    }
}

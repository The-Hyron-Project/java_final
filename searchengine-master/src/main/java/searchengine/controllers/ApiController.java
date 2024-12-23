package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.RequestResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexService;
import searchengine.services.PageIndexService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexService indexService;
    private final PageIndexService pageIndexService;
    private final SearchService searchService;

    public ApiController(StatisticsService statisticsService, IndexService indexService,
        PageIndexService pageIndexService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexService = indexService;
        this.pageIndexService = pageIndexService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<RequestResponse>  startIndexing() {
        return ResponseEntity.ok(indexService.getIndexResult());

    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<RequestResponse> stopIndexing() {
        return ResponseEntity.ok(indexService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<RequestResponse> indexPage(@RequestParam String url)  {
        return ResponseEntity.ok(pageIndexService.startPageIndex(url));
    }

    @GetMapping("/search")
    public ResponseEntity<RequestResponse> search(@RequestParam String query, int offset, int limit, String site) {
        return ResponseEntity.ok(searchService.startSearch(query, offset, limit, site));
    }
}

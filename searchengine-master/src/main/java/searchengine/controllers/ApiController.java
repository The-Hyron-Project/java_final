package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startindexing")
    public ResponseEntity<String> startIndexing() {
        if (indexingService.getIndexResult()) {
            return ResponseEntity.status(HttpStatus.OK).body("\'result\': false\n"
                + "\'error\': \"Индексация уже запущена\"");
        }
        else {
            return ResponseEntity.status(HttpStatus.OK).body("\'result\': true");
        }
    }

    @GetMapping("/stopindexing")
    public ResponseEntity<String> stopIndexing() {
        if (!indexingService.stopIndexing()) {
            return ResponseEntity.status(HttpStatus.OK).body("\'result\': false\n"
                + "\'error\': \"Индексация не запущена\"");
        }
        else {
            return ResponseEntity.status(HttpStatus.OK).body("\'result\': true");
        }
    }
}

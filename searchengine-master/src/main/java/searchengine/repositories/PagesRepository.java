package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Page;

public interface PagesRepository extends JpaRepository<Page, Long> {

}

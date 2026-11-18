package ro.upt.cafe.command.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.upt.cafe.command.domain.MenuItem;

public interface MenuItemRepository extends JpaRepository<MenuItem, String> {
}
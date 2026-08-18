package com.inovexx.inventory_service.repository;

import com.inovexx.inventory_service.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     *  КРИТИЧЕСКИ ВАЖНЫЙ МЕТОД: Поиск с блокировкой строки в БД.
     *
     * Аннотация @Lock(LockModeType.PESSIMISTIC_WRITE) генерирует SQL:
     * "SELECT ... FROM inventory WHERE product_id = ? FOR UPDATE"
     *
     * Это заставляет другие транзакции ждать, пока текущая транзакция
     * не завершит списание товара и не сделает COMMIT.
     * Без этого метода остатки на складе "поплывут" при одновременных заказах.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findWithLockByProductId(@Param("productId") String productId);

    /**
     * 3. Метод для быстрой проверки наличия (опционально).
     */
    @Query("SELECT i.availableStock FROM Inventory i WHERE i.productId = :productId")
    Integer getAvailableStockByProductId(@Param("productId") String productId);
    /**
     * 4. Альтернативный атомарный способ обновления через SQL (если не нужна логика в Java).
     * Позволяет уменьшить остаток и увеличить резерв одним запросом.
     */
    @Modifying
    @Query("UPDATE Inventory i " +
            "SET i.availableStock = i.availableStock - :qty, " +
            "    i.reservedStock = i.reservedStock + :qty " +
            "WHERE i.productId = :productId AND i.availableStock >= :qty")
    int atomicReserve(@Param("productId") String productId, @Param("qty") int qty);

    Optional<Inventory> findByProductId(String productId);

    boolean existsByProductId(String productId);

    void deleteByProductId(String productId);
}
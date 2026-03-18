package com.vibevault.orderservice.repositories;

import com.vibevault.orderservice.models.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    Optional<Order> findByCartEventId(String cartEventId);
}

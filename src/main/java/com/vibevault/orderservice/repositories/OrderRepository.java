package com.vibevault.orderservice.repositories;

import com.vibevault.orderservice.models.Order;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@NullMarked
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = "items")
    Page<Order> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findById(UUID id);

    Optional<Order> findByCartEventId(String cartEventId);
}

package org.cloudstore.controller.repository;

import org.cloudstore.controller.model.StoredObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoredObjectRepository extends JpaRepository<StoredObject, String>
{
    List<StoredObject> findByStatus(String status);
}


package com.vitassistant.repository;

import com.vitassistant.model.entity.IntentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntentQuestionRepository extends JpaRepository<IntentQuestion, Long> {
    List<IntentQuestion> findByEmbeddingProfile(String embeddingProfile);
}

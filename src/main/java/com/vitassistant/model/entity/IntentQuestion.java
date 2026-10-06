package com.vitassistant.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "intent_question")
@Getter
@Setter
@NoArgsConstructor
public class IntentQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String intent;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, length = 2000)
    private String question;

    @Lob
    @Column(nullable = false)
    private String embedding;

    @Column(nullable = false)
    private String embeddingProfile;

    public IntentQuestion(
            String intent,
            String category,
            String question,
            String embedding,
            String embeddingProfile
    ) {
        this.intent = intent;
        this.category = category;
        this.question = question;
        this.embedding = embedding;
        this.embeddingProfile = embeddingProfile;
    }
}

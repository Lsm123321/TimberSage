package com.arch3d.repository;

import com.arch3d.entity.CustomVoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomVoiceRepository extends JpaRepository<CustomVoice, Integer> {

    List<CustomVoice> findByStatusOrderByCreatedAtDesc(String status);

    List<CustomVoice> findAllByOrderByCreatedAtDesc();

    Optional<CustomVoice> findByVoiceIdAndStatus(String voiceId, String status);
}

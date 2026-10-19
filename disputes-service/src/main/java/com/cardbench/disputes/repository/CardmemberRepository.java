package com.cardbench.disputes.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cardbench.disputes.domain.Cardmember;

public interface CardmemberRepository extends JpaRepository<Cardmember, String> {
}

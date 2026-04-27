package com.devblo.transactionservice.repositories;

import com.devblo.transactionservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IAccountRepository extends JpaRepository<Account, Long> {
}

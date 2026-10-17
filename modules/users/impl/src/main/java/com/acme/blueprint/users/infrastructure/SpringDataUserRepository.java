package com.acme.blueprint.users.infrastructure;

import java.util.UUID;

import com.acme.blueprint.users.domain.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Spring Data's view of the user table. Package-private: only the adapter uses it. */
interface SpringDataUserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    boolean existsByNationalCode(String nationalCode);
}

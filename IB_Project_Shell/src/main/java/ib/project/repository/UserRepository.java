package ib.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ib.project.entity.User;

public interface UserRepository extends JpaRepository<User, Long>{

}

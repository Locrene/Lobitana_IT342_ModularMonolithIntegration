package edu.cit.lobitana.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByOrderByNotificationIdDesc();
}

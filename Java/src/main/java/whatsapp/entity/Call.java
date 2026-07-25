package whatsapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "calls", indexes = {
        @Index(name = "idx_calls_caller_receiver_time", columnList = "caller, receiver, start_time"),
        @Index(name = "idx_calls_receiver_time", columnList = "receiver, start_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Call {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String caller;

    @Column(nullable = false)
    private String receiver;

    @Column(name = "call_type", nullable = false)
    private String callType;

    @Column(nullable = false)
    private String status;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_in_seconds")
    private Long durationInSeconds;
}
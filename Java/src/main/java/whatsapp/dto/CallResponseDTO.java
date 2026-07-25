package whatsapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallResponseDTO {
    private Long id;
    private String caller;
    private String receiver;
    private String callType;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationInSeconds;
}
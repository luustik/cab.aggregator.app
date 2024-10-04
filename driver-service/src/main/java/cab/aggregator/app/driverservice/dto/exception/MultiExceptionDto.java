package cab.aggregator.app.driverservice.dto.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.Map;

@Schema(description = "Multi exception DTO")
@Builder
public record MultiExceptionDto (
        @Schema(description = "Status")
        Integer status,

        @Schema(description = "Message")
        String message,

        @Schema(description = "Errors")
        Map<String, String> errors
){
}
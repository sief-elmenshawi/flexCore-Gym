package com.flexcore.subscription.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Family group details")
public class FamilyGroupResponse {

    @Schema(description = "Family group id", example = "3")
    private Long id;

    @Schema(description = "Owner user id", example = "5")
    private Long ownerUserId;

    @Schema(description = "Owner full name", example = "Sara Ali")
    private String ownerName;

    @Schema(description = "Plan id", example = "2")
    private Long planId;

    @Schema(description = "Plan name", example = "Family Monthly")
    private String planName;

    @Schema(description = "Maximum members allowed", example = "4")
    private Integer maxMembers;
}

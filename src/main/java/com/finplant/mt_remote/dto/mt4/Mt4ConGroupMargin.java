package com.finplant.mt_remote.dto.mt4;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Mt4ConGroupMargin {

    @JsonProperty("swap_long")
    private BigDecimal swapLong;

    @JsonProperty("swap_short")
    private BigDecimal swapShort;

    @JsonProperty("margin_pct")
    private BigDecimal marginPct;

    @Builder(toBuilder = true)
    public Mt4ConGroupMargin(BigDecimal swapLong, BigDecimal swapShort, BigDecimal marginPct) {
        this.swapLong = swapLong;
        this.swapShort = swapShort;
        this.marginPct = marginPct;
    }
}

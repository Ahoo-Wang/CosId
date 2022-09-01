package me.ahoo.cosid.example.jdbc.dto;

import me.ahoo.cosid.jackson.AsString;

import lombok.Data;

/** Dto for jackson.
 *
 * @author : Rocher Kong
 */
@Data
public class AsStringDto {
    @AsString
    private Long id;

    @AsString(AsString.Type.RADIX)
    private Long radixId;

    @AsString(value = AsString.Type.RADIX, radixPadStart = true)
    private Long radixPadStartId;

    @AsString(value = AsString.Type.RADIX, radixPadStart = true, radixCharSize = 10)
    private Long radixPadStartCharSize10Id;

    @AsString(AsString.Type.FRIENDLY_ID)
    private long friendlyId;
}

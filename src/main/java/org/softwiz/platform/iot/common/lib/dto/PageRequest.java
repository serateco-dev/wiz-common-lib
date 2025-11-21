package org.softwiz.platform.iot.common.lib.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {
    
    @Builder.Default
    private Integer page = 1;
    
    @Builder.Default
    private Integer pageSize = 20;
    
    private Long offset;
    
    public void init() {
        if (this.page == null || this.page < 1) {
            this.page = 1;
        }
        if (this.pageSize == null || this.pageSize < 1) {
            this.pageSize = 20;
        }
        this.offset = (this.page - 1) * this.pageSize;
    }
    
    public Integer getOffset() {
        if (this.offset == null) {
            init();
        }
        return this.offset;
    }
}
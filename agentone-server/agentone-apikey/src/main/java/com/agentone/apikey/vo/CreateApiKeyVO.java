package com.agentone.apikey.vo;

import lombok.Data;

/**
 * 创建 API Key 响应 - 仅创建时返回完整明文 Key
 */
@Data
public class CreateApiKeyVO {

    private String id;

    /** 完整明文 Key，仅创建时返回一次 */
    private String apiKey;

    private String keyPrefix;
    private String env;
    private String status;
}

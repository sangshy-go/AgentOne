package com.agentone.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应包装
 * data 结构: { records, total, current, size, pages }
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records;
    private long total;
    private long current;
    private long size;
    private long pages;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(page.getRecords());
        r.setTotal(page.getTotal());
        r.setCurrent(page.getCurrent());
        r.setSize(page.getSize());
        r.setPages(page.getPages());
        return r;
    }
}

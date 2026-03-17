package com.campusfit.modules.tag.service.impl;

import com.campusfit.modules.tag.service.TagService;
import com.campusfit.modules.tag.vo.TagOptionsVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TagServiceImpl implements TagService {

    private final JdbcTemplate jdbcTemplate;

    public TagServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TagOptionsVO getOptions() {
        return new TagOptionsVO(
            listByCategory("scene"),
            listByCategory("style"),
            listByCategory("budget")
        );
    }

    private List<String> listByCategory(String category) {
        List<String> values = jdbcTemplate.queryForList(
            "select option_value from tag_option where category = ? and status = 1 order by sort_order asc, id asc",
            String.class,
            category
        );
        return values == null ? new ArrayList<>() : values;
    }
}

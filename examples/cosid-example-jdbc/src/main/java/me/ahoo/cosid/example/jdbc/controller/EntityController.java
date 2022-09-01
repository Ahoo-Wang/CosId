package me.ahoo.cosid.example.jdbc.controller;

import me.ahoo.cosid.example.jdbc.dto.AsStringDto;
import me.ahoo.cosid.example.jdbc.entity.FriendlyIdEntity;
import me.ahoo.cosid.example.jdbc.entity.LongIdEntity;
import me.ahoo.cosid.example.jdbc.repository.EntityRepository;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entity Controller.
 *
 * @author Rocher Kong
 */
@RestController
@RequestMapping("test")
public class EntityController {

    private final EntityRepository entityRepository;

    public EntityController(EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @PostMapping("/long")
    public LongIdEntity longId() {
        LongIdEntity entity = new LongIdEntity();
        entityRepository.insert(entity);
        /**
         * {
         *   "id": 208796080181248
         * }
         */
        return entity;
    }

    @PostMapping("/friendly")
    public FriendlyIdEntity friendly() {
        FriendlyIdEntity entity = new FriendlyIdEntity();
        entityRepository.insertFriendly(entity);
        return entity;
    }

    @PostMapping("/asStringDto")
    public AsStringDto getJacksonDto() throws Exception {
        AsStringDto dto = new AsStringDto();
        dto.setId(123456L);
        dto.setFriendlyId(123456L);
        dto.setRadixPadStartId(123456L);
        dto.setRadixPadStartCharSize10Id(123456L);
        dto.setRadixId(123456L);
        return dto;
    }
}

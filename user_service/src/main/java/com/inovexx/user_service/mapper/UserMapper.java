package com.inovexx.user_service.mapper;

import com.inovexx.user_service.dto.UserDto;
import com.inovexx.user_service.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserDto userToUserDto(User user);

    User userDtoToUser(UserDto userDto);

    @Mapping(target = "id", ignore = true) // Защита ID от изменений
    void updateUserFromDto(UserDto userDto, @MappingTarget User user);
}

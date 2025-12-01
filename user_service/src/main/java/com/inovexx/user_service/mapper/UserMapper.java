package com.inovexx.user_service.mapper;

import com.inovexx.user_service.entity.User;
import com.inovexx.user_service.entity.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserDto userToUserDto(User user);

    User userDtoToUser(UserDto userDto);

    void updateUserFromDto(UserDto userDto, @MappingTarget User user);
}

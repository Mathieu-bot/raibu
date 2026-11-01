package shi.raibu.shi.endpoint.rest.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shi.raibu.shi.endpoint.rest.dto.PublicUserDto;
import shi.raibu.shi.repository.UserRepository;

@RestController
@RequestMapping("/users")
@AllArgsConstructor
public class UserController {
  private final UserRepository userRepository;

  @GetMapping("/{userId}")
  public ResponseEntity<PublicUserDto> getUser(@PathVariable String userId) {
    return userRepository
        .findById(userId)
        .map(PublicUserDto::from)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}

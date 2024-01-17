package server.player.controller;

import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;
import server.player.model.AccountCharactersResponse;
import server.player.model.Character;
import server.player.model.CreateCharacterRequest;
import server.player.service.PlayerCharacterService;

import javax.validation.Valid;
import java.util.List;

// @Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/player")
public class PlayerController {

    @Inject PlayerCharacterService playerCharacterService;

    @Get("/account-characters")
    public AccountCharactersResponse getAccountCharacters(@Header String accountName) {
        // This endpoint will be for when user logs in.
        // They will be greeted with a list of characters
        return playerCharacterService.getAccountCharacters(accountName);
    }

    @Post("/create-character")
    public Character createCharacter(
            @Body @Valid CreateCharacterRequest createCharacterRequest,
            @Header String accountName) {

        return playerCharacterService.createCharacter(createCharacterRequest, accountName);
    }

    @Get("/characters")
    public AccountCharactersResponse getCharacters(@QueryValue List<String> names) {

        return playerCharacterService.getCharacters(names);
    }

    @Delete(value = "/{actorId}")
    public void deleteCharacter(@Header String accountName, String actorId) {
        // TODO: validation that character belongs to account
        playerCharacterService.deleteCharacter(actorId);
    }
}

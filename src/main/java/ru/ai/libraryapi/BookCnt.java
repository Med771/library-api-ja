package ru.ai.libraryapi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST контроллер для работы с EPUB книгами.
 * 
 * Предоставляет API для разбора EPUB файлов и получения их страниц
 * в удобном для чтения формате.
 */
@Slf4j
@RestController
@RequestMapping(path = "epub")
@RequiredArgsConstructor
@Tag(name = "EPUB Books", description = "API для работы с EPUB книгами")
public class BookCnt {
    
    private final BookServ bookServ;

    /**
     * Получение страниц из EPUB файла.
     * 
     * Принимает путь к EPUB файлу и диапазон страниц для возврата.
     * Разбирает EPUB файл, очищает HTML контент и возвращает страницы
     * в удобном для чтения формате.
     * 
     * @param reqDTO запрос с путем к файлу и диапазоном страниц
     * @return список страниц книги
     */
    @Operation(
            summary = "Получение страниц EPUB",
            description = "Разбирает EPUB-файл по указанному пути и возвращает список страниц. " +
                    "Если произошла ошибка при разборе, возвращается пустой список, но код HTTP = 200.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Успешный ответ. Пустой список означает ошибку при разборе EPUB",
                            content = @Content(
                                    mediaType = "application/json", 
                                    schema = @Schema(implementation = ResDTO.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Некорректные параметры запроса"
                    )
            }
    )
    @PostMapping("pages")
    public ResponseEntity<ResDTO> getPages(@Valid @RequestBody ReqDTO reqDTO) {
        log.info("Получен запрос на разбор EPUB: {}", reqDTO.path());
        
        ResDTO response = bookServ.getPages(reqDTO);
        
        log.info("Возвращено {} страниц для файла: {}", 
                response.pages().size(), reqDTO.path());
        
        return ResponseEntity.ok(response);
    }
}
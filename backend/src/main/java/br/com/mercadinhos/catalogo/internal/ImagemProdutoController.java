package br.com.mercadinhos.catalogo.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/produtos/{id}/imagem")
@RequiredArgsConstructor
@Tag(name = "Produtos")
@SecurityRequirement(name = "sessao")
class ImagemProdutoController {
    private final ImagemProdutoService imagens;

    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Consultar imagem protegida do produto", description = "Exige CONSULTAR. Cookie de sessão necessário; não é uma URL pública.")
    @ApiResponse(responseCode = "404", description = "Produto ou imagem não encontrados na organização")
    ResponseEntity<byte[]> ler(@PathVariable @Positive Long id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"produto.png\"").body(imagens.ler(id));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Enviar ou substituir imagem", description = "Exige EDITAR, CSRF, arquivo PNG/JPEG até 5 MiB e versão atual. A imagem é regravada em PNG sem os metadados originais.")
    @SecurityRequirement(name = "csrf")
    @ApiResponse(responseCode = "413", description = "Imagem excede o limite")
    @ApiResponse(responseCode = "409", description = "Versão desatualizada")
    ProdutoDTO enviar(@PathVariable @Positive Long id, @RequestParam @PositiveOrZero long versao,
            @RequestPart MultipartFile arquivo) { return imagens.enviar(id, versao, arquivo); }

    @DeleteMapping
    @Operation(summary = "Remover imagem", description = "Exige EDITAR, CSRF e versão atual. Não exclui o produto.")
    @SecurityRequirement(name = "csrf")
    ProdutoDTO remover(@PathVariable @Positive Long id, @RequestParam @PositiveOrZero long versao) { return imagens.remover(id, versao); }
}

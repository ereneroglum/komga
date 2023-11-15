package org.gotson.komga.interfaces.api

import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.model.MediaProfile
import org.gotson.komga.domain.model.SeriesMetadata
import org.gotson.komga.domain.service.BookAnalyzer
import org.gotson.komga.infrastructure.image.ImageConverter
import org.gotson.komga.infrastructure.image.ImageType
import org.gotson.komga.infrastructure.jooq.toCurrentTimeZone
import org.gotson.komga.interfaces.api.dto.MEDIATYPE_DIVINA_JSON
import org.gotson.komga.interfaces.api.dto.MEDIATYPE_DIVINA_JSON_VALUE
import org.gotson.komga.interfaces.api.dto.MEDIATYPE_OPDS_JSON_VALUE
import org.gotson.komga.interfaces.api.dto.MEDIATYPE_OPDS_PUBLICATION_JSON
import org.gotson.komga.interfaces.api.dto.MEDIATYPE_WEBPUB_JSON
import org.gotson.komga.interfaces.api.dto.MEDIATYPE_WEBPUB_JSON_VALUE
import org.gotson.komga.interfaces.api.dto.OpdsLinkRel
import org.gotson.komga.interfaces.api.dto.PROFILE_DIVINA
import org.gotson.komga.interfaces.api.dto.PROFILE_PDF
import org.gotson.komga.interfaces.api.dto.WPBelongsToDto
import org.gotson.komga.interfaces.api.dto.WPContributorDto
import org.gotson.komga.interfaces.api.dto.WPLinkDto
import org.gotson.komga.interfaces.api.dto.WPMetadataDto
import org.gotson.komga.interfaces.api.dto.WPPublicationDto
import org.gotson.komga.interfaces.api.dto.WPReadingProgressionDto
import org.gotson.komga.interfaces.api.rest.dto.AuthorDto
import org.gotson.komga.interfaces.api.rest.dto.BookDto
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import org.springframework.web.util.UriComponentsBuilder
import java.time.ZoneId
import java.time.ZonedDateTime
import org.gotson.komga.domain.model.MediaType as KomgaMediaType

@Service
class WebPubGenerator(
  @Qualifier("pdfImageType")
  private val pdfImageType: ImageType,
  @Qualifier("thumbnailType")
  private val thumbnailType: ImageType,
  private val imageConverter: ImageConverter,
  private val bookAnalyzer: BookAnalyzer,
) {
  private val wpKnownRoles = listOf(
    "author",
    "translator",
    "editor",
    "artist",
    "illustrator",
    "letterer",
    "penciler",
    "penciller",
    "colorist",
    "inker",
  )

  private val recommendedImageMediaTypes = listOf("image/jpeg", "image/png", "image/gif")

  private fun BookDto.toBasePublicationDto(includeOpdsLinks: Boolean = false): WPPublicationDto {
    val uriBuilder = ServletUriComponentsBuilder.fromCurrentContextPath().pathSegment("api", "v1")
    return WPPublicationDto(
      mediaType = MEDIATYPE_OPDS_PUBLICATION_JSON,
      context = "https://readium.org/webpub-manifest/context.jsonld",
      metadata = toWPMetadataDto(includeOpdsLinks).withAuthors(metadata.authors),
      links = toWPLinkDtos(uriBuilder),
    )
  }

  fun toOpdsPublicationDto(bookDto: BookDto, includeOpdsLinks: Boolean = false): WPPublicationDto {
    return bookDto.toBasePublicationDto(includeOpdsLinks).copy(images = buildThumbnailLinkDtos(bookDto.id))
  }

  private fun buildThumbnailLinkDtos(bookId: String) = listOf(
    WPLinkDto(
      href = ServletUriComponentsBuilder.fromCurrentContextPath().pathSegment("api", "v1").path("books/$bookId/thumbnail").toUriString(),
      type = thumbnailType.mediaType,
    ),
  )

  fun toManifestDivina(bookDto: BookDto, media: Media, seriesMetadata: SeriesMetadata): WPPublicationDto {
    val uriBuilder = ServletUriComponentsBuilder.fromCurrentContextPath().pathSegment("api", "v1")
    return bookDto.toBasePublicationDto().let {
      val pages = if (media.profile == MediaProfile.PDF) bookAnalyzer.getPdfPagesDynamic(media) else media.pages
      it.copy(
        mediaType = MEDIATYPE_DIVINA_JSON,
        metadata = it.metadata
          .withSeriesMetadata(seriesMetadata)
          .copy(conformsTo = PROFILE_DIVINA),
        readingOrder = pages.mapIndexed { index: Int, page: BookPage ->
          WPLinkDto(
            href = uriBuilder.cloneBuilder().path("books/${bookDto.id}/pages/${index + 1}").toUriString(),
            type = page.mediaType,
            width = page.dimension?.width,
            height = page.dimension?.height,
            alternate = if (!recommendedImageMediaTypes.contains(page.mediaType) && imageConverter.canConvertMediaType(page.mediaType, MediaType.IMAGE_JPEG_VALUE)) listOf(
              WPLinkDto(
                href = uriBuilder.cloneBuilder().path("books/${bookDto.id}/pages/${index + 1}").queryParam("convert", "jpeg").toUriString(),
                type = MediaType.IMAGE_JPEG_VALUE,
                width = page.dimension?.width,
                height = page.dimension?.height,
              ),
            ) else emptyList(),
          )
        },
        resources = buildThumbnailLinkDtos(bookDto.id),
      )
    }
  }

  fun toManifestPdf(bookDto: BookDto, media: Media, seriesMetadata: SeriesMetadata): WPPublicationDto {
    val uriBuilder = ServletUriComponentsBuilder.fromCurrentContextPath().pathSegment("api", "v1")
    return bookDto.toBasePublicationDto().let {
      it.copy(
        mediaType = MEDIATYPE_WEBPUB_JSON,
        metadata = it.metadata
          .withSeriesMetadata(seriesMetadata)
          .copy(conformsTo = PROFILE_PDF),
        readingOrder = List(media.pageCount) { index: Int ->
          WPLinkDto(
            href = uriBuilder.cloneBuilder().path("books/${bookDto.id}/pages/${index + 1}/raw").toUriString(),
            type = KomgaMediaType.PDF.type,
          )
        },
        resources = buildThumbnailLinkDtos(bookDto.id),
      )
    }
  }

  private fun BookDto.toWPMetadataDto(includeOpdsLinks: Boolean = false) = WPMetadataDto(
    title = metadata.title,
    description = metadata.summary,
    numberOfPages = this.media.pagesCount,
    modified = lastModified.toCurrentTimeZone().atZone(ZoneId.systemDefault()) ?: ZonedDateTime.now(),
    published = metadata.releaseDate,
    subject = metadata.tags.toList(),
    identifier = if (metadata.isbn.isNotBlank()) "urn:isbn:${metadata.isbn}" else null,
    belongsTo = WPBelongsToDto(
      series = listOf(
        WPContributorDto(
          seriesTitle,
          metadata.numberSort,
          if (includeOpdsLinks) listOf(
            WPLinkDto(
              href = ServletUriComponentsBuilder.fromCurrentContextPath().pathSegment("opds", "v2").path("series/$seriesId").toUriString(),
              type = MEDIATYPE_OPDS_JSON_VALUE,
            ),
          ) else emptyList(),
        ),
      ),
    ),
  )

  private fun WPMetadataDto.withSeriesMetadata(seriesMetadata: SeriesMetadata) =
    copy(
      language = seriesMetadata.language,
      readingProgression = when (seriesMetadata.readingDirection) {
        SeriesMetadata.ReadingDirection.LEFT_TO_RIGHT -> WPReadingProgressionDto.LTR
        SeriesMetadata.ReadingDirection.RIGHT_TO_LEFT -> WPReadingProgressionDto.RTL
        SeriesMetadata.ReadingDirection.VERTICAL -> WPReadingProgressionDto.TTB
        SeriesMetadata.ReadingDirection.WEBTOON -> WPReadingProgressionDto.TTB
        null -> null
      },
    )

  private fun WPMetadataDto.withAuthors(authors: List<AuthorDto>): WPMetadataDto {
    val groups = authors.groupBy({ it.role }, { it.name })
    return copy(
      author = groups["author"].orEmpty(),
      translator = groups["translator"].orEmpty(),
      editor = groups["editor"].orEmpty(),
      artist = groups["artist"].orEmpty(),
      illustrator = groups["illustrator"].orEmpty(),
      letterer = groups["letterer"].orEmpty(),
      penciler = groups["penciler"].orEmpty() + groups["penciller"].orEmpty(),
      colorist = groups["colorist"].orEmpty(),
      inker = groups["inker"].orEmpty(),
      // use contributor role for all roles not mentioned above
      contributor = authors.filterNot { wpKnownRoles.contains(it.role) }.map { it.name },
    )
  }

  private fun BookDto.toWPLinkDtos(uriBuilder: UriComponentsBuilder): List<WPLinkDto> {
    val komgaMediaType = KomgaMediaType.fromMediaType(media.mediaType)
    return listOfNotNull(
      // most appropriate manifest
      WPLinkDto(rel = OpdsLinkRel.SELF, href = uriBuilder.cloneBuilder().path("books/$id/manifest").toUriString(), type = mediaProfileToWebPub(komgaMediaType?.profile)),
      // PDF is also available under the Divina profile
      if (komgaMediaType?.profile == MediaProfile.PDF) WPLinkDto(href = uriBuilder.cloneBuilder().path("books/$id/manifest/divina").toUriString(), type = MEDIATYPE_DIVINA_JSON_VALUE) else null,
      // main acquisition link
      WPLinkDto(rel = OpdsLinkRel.ACQUISITION, type = komgaMediaType?.exportType ?: media.mediaType, href = uriBuilder.cloneBuilder().path("books/$id/file").toUriString()),
    )
  }

  private fun mediaProfileToWebPub(profile: MediaProfile?): String = when (profile) {
    MediaProfile.DIVINA -> MEDIATYPE_DIVINA_JSON_VALUE
    MediaProfile.PDF -> MEDIATYPE_WEBPUB_JSON_VALUE
    null -> MEDIATYPE_WEBPUB_JSON_VALUE
  }
}

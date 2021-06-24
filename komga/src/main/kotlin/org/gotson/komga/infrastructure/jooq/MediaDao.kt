package org.gotson.komga.infrastructure.jooq

import org.gotson.komga.domain.model.BookPage
import org.gotson.komga.domain.model.Dimension
import org.gotson.komga.domain.model.Media
import org.gotson.komga.domain.persistence.MediaRepository
import org.gotson.komga.jooq.Tables
import org.gotson.komga.jooq.tables.records.MediaPageRecord
import org.gotson.komga.jooq.tables.records.MediaRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class MediaDao(
  private val dsl: DSLContext
) : MediaRepository {

  private val m = Tables.MEDIA
  private val p = Tables.MEDIA_PAGE
  private val f = Tables.MEDIA_FILE

  private val groupFields = arrayOf(*m.fields(), *p.fields())

  override fun findById(bookId: String): Media =
    find(dsl, bookId)

  override fun getPagesSize(bookId: String): Int =
    dsl.select(m.PAGE_COUNT)
      .from(m)
      .where(m.BOOK_ID.eq(bookId))
      .fetch(m.PAGE_COUNT)
      .first()

  override fun getPagesSizes(bookIds: Collection<String>): Collection<Pair<String, Int>> =
    dsl.select(m.BOOK_ID, m.PAGE_COUNT)
      .from(m)
      .where(m.BOOK_ID.`in`(bookIds))
      .fetch()
      .map { Pair(it[m.BOOK_ID], it[m.PAGE_COUNT]) }

  private fun find(dsl: DSLContext, bookId: String): Media =
    dsl.select(*groupFields)
      .from(m)
      .leftJoin(p).on(m.BOOK_ID.eq(p.BOOK_ID))
      .where(m.BOOK_ID.eq(bookId))
      .groupBy(*groupFields)
      .orderBy(p.NUMBER.asc())
      .fetchGroups(
        { it.into(m) }, { it.into(p) }
      ).map { (mr, pr) ->
        val files = dsl.selectFrom(f)
          .where(f.BOOK_ID.eq(bookId))
          .fetchInto(f)
          .map { it.fileName }

        mr.toDomain(pr.filterNot { it.bookId == null }.map { it.toDomain() }, files)
      }.first()

  @Transactional
  override fun insert(media: Media) {
    insert(listOf(media))
  }

  @Transactional
  override fun insert(medias: Collection<Media>) {
    if (medias.isNotEmpty()) {
      dsl.batch(
        dsl.insertInto(
          m,
          m.BOOK_ID,
          m.STATUS,
          m.MEDIA_TYPE,
          m.COMMENT,
          m.PAGE_COUNT
        ).values(null as String?, null, null, null, null)
      ).also { step ->
        medias.forEach {
          step.bind(
            it.bookId,
            it.status,
            it.mediaType,
            it.comment,
            it.pages.size
          )
        }
      }.execute()

      insertPages(medias)
      insertFiles(medias)
    }
  }

  private fun insertPages(medias: Collection<Media>) {
    if (medias.any { it.pages.isNotEmpty() }) {
      dsl.batch(
        dsl.insertInto(
          p,
          p.BOOK_ID,
          p.FILE_NAME,
          p.MEDIA_TYPE,
          p.NUMBER,
          p.WIDTH,
          p.HEIGHT
        ).values(null as String?, null, null, null, null, null)
      ).also {
        medias.forEach { media ->
          media.pages.forEachIndexed { index, page ->
            it.bind(
              media.bookId,
              page.fileName,
              page.mediaType,
              index,
              page.dimension?.width,
              page.dimension?.height
            )
          }
        }
      }.execute()
    }
  }

  private fun insertFiles(medias: Collection<Media>) {
    if (medias.any { it.files.isNotEmpty() }) {
      dsl.batch(
        dsl.insertInto(
          f,
          f.BOOK_ID,
          f.FILE_NAME
        ).values(null as String?, null)
      ).also { step ->
        medias.forEach { media ->
          media.files.forEach {
            step.bind(
              media.bookId,
              it
            )
          }
        }
      }.execute()
    }
  }

  @Transactional
  override fun update(media: Media) {
    dsl.update(m)
      .set(m.STATUS, media.status.toString())
      .set(m.MEDIA_TYPE, media.mediaType)
      .set(m.COMMENT, media.comment)
      .set(m.PAGE_COUNT, media.pages.size)
      .set(m.LAST_MODIFIED_DATE, LocalDateTime.now(ZoneId.of("Z")))
      .where(m.BOOK_ID.eq(media.bookId))
      .execute()

    dsl.deleteFrom(p)
      .where(p.BOOK_ID.eq(media.bookId))
      .execute()

    dsl.deleteFrom(f)
      .where(f.BOOK_ID.eq(media.bookId))
      .execute()

    insertPages(listOf(media))
    insertFiles(listOf(media))
  }

  @Transactional
  override fun delete(bookId: String) {
    dsl.deleteFrom(p).where(p.BOOK_ID.eq(bookId)).execute()
    dsl.deleteFrom(f).where(f.BOOK_ID.eq(bookId)).execute()
    dsl.deleteFrom(m).where(m.BOOK_ID.eq(bookId)).execute()
  }

  @Transactional
  override fun deleteByBookIds(bookIds: Collection<String>) {
    dsl.deleteFrom(p).where(p.BOOK_ID.`in`(bookIds)).execute()
    dsl.deleteFrom(f).where(f.BOOK_ID.`in`(bookIds)).execute()
    dsl.deleteFrom(m).where(m.BOOK_ID.`in`(bookIds)).execute()
  }

  override fun count(): Long = dsl.fetchCount(m).toLong()

  private fun MediaRecord.toDomain(pages: List<BookPage>, files: List<String>) =
    Media(
      status = Media.Status.valueOf(status),
      mediaType = mediaType,
      pages = pages,
      files = files,
      comment = comment,
      bookId = bookId,
      createdDate = createdDate.toCurrentTimeZone(),
      lastModifiedDate = lastModifiedDate.toCurrentTimeZone()
    )

  private fun MediaPageRecord.toDomain() =
    BookPage(
      fileName = fileName,
      mediaType = mediaType,
      dimension = if (width != null && height != null) Dimension(width, height) else null
    )
}

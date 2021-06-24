package org.gotson.komga.infrastructure.jooq

import org.gotson.komga.domain.model.ThumbnailBook
import org.gotson.komga.domain.persistence.ThumbnailBookRepository
import org.gotson.komga.jooq.Tables
import org.gotson.komga.jooq.tables.records.ThumbnailBookRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.net.URL

@Component
class ThumbnailBookDao(
  private val dsl: DSLContext
) : ThumbnailBookRepository {
  private val tb = Tables.THUMBNAIL_BOOK

  override fun findAllByBookId(bookId: String): Collection<ThumbnailBook> =
    dsl.selectFrom(tb)
      .where(tb.BOOK_ID.eq(bookId))
      .fetchInto(tb)
      .map { it.toDomain() }

  override fun findAllByBookIdAndType(bookId: String, type: ThumbnailBook.Type): Collection<ThumbnailBook> =
    dsl.selectFrom(tb)
      .where(tb.BOOK_ID.eq(bookId))
      .and(tb.TYPE.eq(type.toString()))
      .fetchInto(tb)
      .map { it.toDomain() }

  override fun findSelectedByBookIdOrNull(bookId: String): ThumbnailBook? =
    dsl.selectFrom(tb)
      .where(tb.BOOK_ID.eq(bookId))
      .and(tb.SELECTED.isTrue)
      .limit(1)
      .fetchInto(tb)
      .map { it.toDomain() }
      .firstOrNull()

  @Transactional
  override fun insert(thumbnail: ThumbnailBook) {
    dsl.insertInto(tb)
      .set(tb.ID, thumbnail.id)
      .set(tb.BOOK_ID, thumbnail.bookId)
      .set(tb.THUMBNAIL, thumbnail.thumbnail)
      .set(tb.URL, thumbnail.url?.toString())
      .set(tb.SELECTED, thumbnail.selected)
      .set(tb.TYPE, thumbnail.type.toString())
      .execute()
  }

  @Transactional
  override fun update(thumbnail: ThumbnailBook) {
    dsl.update(tb)
      .set(tb.BOOK_ID, thumbnail.bookId)
      .set(tb.THUMBNAIL, thumbnail.thumbnail)
      .set(tb.URL, thumbnail.url?.toString())
      .set(tb.SELECTED, thumbnail.selected)
      .set(tb.TYPE, thumbnail.type.toString())
      .where(tb.ID.eq(thumbnail.id))
      .execute()
  }

  @Transactional
  override fun markSelected(thumbnail: ThumbnailBook) {
    dsl.update(tb)
      .set(tb.SELECTED, false)
      .where(tb.BOOK_ID.eq(thumbnail.bookId))
      .and(tb.ID.ne(thumbnail.id))
      .execute()

    dsl.update(tb)
      .set(tb.SELECTED, true)
      .where(tb.BOOK_ID.eq(thumbnail.bookId))
      .and(tb.ID.eq(thumbnail.id))
      .execute()
  }

  @Transactional
  override fun delete(thumbnailBookId: String) {
    dsl.deleteFrom(tb).where(tb.ID.eq(thumbnailBookId)).execute()
  }

  @Transactional
  override fun deleteByBookId(bookId: String) {
    dsl.deleteFrom(tb).where(tb.BOOK_ID.eq(bookId)).execute()
  }

  @Transactional
  override fun deleteByBookIds(bookIds: Collection<String>) {
    dsl.deleteFrom(tb).where(tb.BOOK_ID.`in`(bookIds)).execute()
  }

  @Transactional
  override fun deleteByBookIdAndType(bookId: String, type: ThumbnailBook.Type) {
    dsl.deleteFrom(tb)
      .where(tb.BOOK_ID.eq(bookId))
      .and(tb.TYPE.eq(type.toString()))
      .execute()
  }

  private fun ThumbnailBookRecord.toDomain() =
    ThumbnailBook(
      thumbnail = thumbnail,
      url = url?.let { URL(it) },
      selected = selected,
      type = ThumbnailBook.Type.valueOf(type),
      id = id,
      bookId = bookId,
      createdDate = createdDate,
      lastModifiedDate = lastModifiedDate
    )
}

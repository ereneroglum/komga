package org.gotson.komga.infrastructure.jooq

import org.gotson.komga.domain.model.Author
import org.gotson.komga.domain.model.BookMetadataAggregation
import org.gotson.komga.domain.persistence.BookMetadataAggregationRepository
import org.gotson.komga.jooq.Tables
import org.gotson.komga.jooq.tables.records.BookMetadataAggregationAuthorRecord
import org.gotson.komga.jooq.tables.records.BookMetadataAggregationRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class BookMetadataAggregationDao(
  private val dsl: DSLContext
) : BookMetadataAggregationRepository {

  private val d = Tables.BOOK_METADATA_AGGREGATION
  private val a = Tables.BOOK_METADATA_AGGREGATION_AUTHOR

  private val groupFields = arrayOf(*d.fields(), *a.fields())

  override fun findById(seriesId: String): BookMetadataAggregation =
    findOne(listOf(seriesId)).first()

  override fun findByIdOrNull(seriesId: String): BookMetadataAggregation? =
    findOne(listOf(seriesId)).firstOrNull()

  private fun findOne(seriesIds: Collection<String>) =
    dsl.select(*groupFields)
      .from(d)
      .leftJoin(a).on(d.SERIES_ID.eq(a.SERIES_ID))
      .where(d.SERIES_ID.`in`(seriesIds))
      .groupBy(*groupFields)
      .fetchGroups(
        { it.into(d) }, { it.into(a) }
      ).map { (dr, ar) ->
        dr.toDomain(ar.filterNot { it.name == null }.map { it.toDomain() })
      }

  @Transactional
  override fun insert(metadata: BookMetadataAggregation) {
    dsl.insertInto(d)
      .set(d.SERIES_ID, metadata.seriesId)
      .set(d.RELEASE_DATE, metadata.releaseDate)
      .set(d.SUMMARY, metadata.summary)
      .set(d.SUMMARY_NUMBER, metadata.summaryNumber)
      .execute()

    insertAuthors(metadata)
  }

  @Transactional
  override fun update(metadata: BookMetadataAggregation) {
    dsl.update(d)
      .set(d.SUMMARY, metadata.summary)
      .set(d.SUMMARY_NUMBER, metadata.summaryNumber)
      .set(d.RELEASE_DATE, metadata.releaseDate)
      .set(d.LAST_MODIFIED_DATE, LocalDateTime.now(ZoneId.of("Z")))
      .where(d.SERIES_ID.eq(metadata.seriesId))
      .execute()

    dsl.deleteFrom(a)
      .where(a.SERIES_ID.eq(metadata.seriesId))
      .execute()

    insertAuthors(metadata)
  }

  private fun insertAuthors(metadata: BookMetadataAggregation) {
    if (metadata.authors.isNotEmpty()) {
      dsl.batch(
        dsl.insertInto(a, a.SERIES_ID, a.NAME, a.ROLE)
          .values(null as String?, null, null)
      ).also { step ->
        metadata.authors.forEach {
          step.bind(metadata.seriesId, it.name, it.role)
        }
      }.execute()
    }
  }

  @Transactional
  override fun delete(seriesId: String) {
    dsl.deleteFrom(a).where(a.SERIES_ID.eq(seriesId)).execute()
    dsl.deleteFrom(d).where(d.SERIES_ID.eq(seriesId)).execute()
  }

  @Transactional
  override fun delete(seriesIds: Collection<String>) {
    dsl.deleteFrom(a).where(a.SERIES_ID.`in`(seriesIds)).execute()
    dsl.deleteFrom(d).where(d.SERIES_ID.`in`(seriesIds)).execute()
  }

  override fun count(): Long = dsl.fetchCount(d).toLong()

  private fun BookMetadataAggregationRecord.toDomain(authors: List<Author>) =
    BookMetadataAggregation(
      authors = authors,
      releaseDate = releaseDate,
      summary = summary,
      summaryNumber = summaryNumber,

      seriesId = seriesId,

      createdDate = createdDate.toCurrentTimeZone(),
      lastModifiedDate = lastModifiedDate.toCurrentTimeZone()
    )

  private fun BookMetadataAggregationAuthorRecord.toDomain() =
    Author(
      name = name,
      role = role
    )
}

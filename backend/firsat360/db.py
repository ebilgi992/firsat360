"""Veritabanı modeli (SQLAlchemy 2). Üretimde PostgreSQL, testlerde SQLite da desteklenir."""
from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import (Boolean, Date, DateTime, ForeignKey, Index, Integer, Numeric, String, Text,
                        create_engine, func, select)
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, relationship, sessionmaker


class Base(DeclarativeBase):
    pass


class Store(Base):
    __tablename__ = "stores"
    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    name: Mapped[str] = mapped_column(String(64))
    official_url: Mapped[str] = mapped_column(String(512))
    catalog_url: Mapped[str] = mapped_column(String(512))
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    # ok | partial | link_only | error | never_run | disabled
    status: Mapped[str] = mapped_column(String(16), default="never_run")
    status_message: Mapped[str | None] = mapped_column(Text, nullable=True)
    last_checked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    last_success_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    last_error: Mapped[str | None] = mapped_column(Text, nullable=True)
    legal_note: Mapped[str | None] = mapped_column(Text, nullable=True)
    # official_cdn: görseller mağazanın kendi CDN adresinden gösterilir (kopyalanmaz) · link_only: görsel gösterilmez
    image_policy: Mapped[str] = mapped_column(String(16), default="official_cdn", nullable=True)


class SourceRun(Base):
    __tablename__ = "source_runs"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    store_id: Mapped[str] = mapped_column(ForeignKey("stores.id"))
    method: Mapped[str] = mapped_column(String(48))
    trigger: Mapped[str] = mapped_column(String(16), default="schedule")  # schedule | manual | snapshot
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    ok: Mapped[bool] = mapped_column(Boolean, default=False)
    catalogs: Mapped[int] = mapped_column(Integer, default=0)
    offers: Mapped[int] = mapped_column(Integer, default=0)
    new_offers: Mapped[int] = mapped_column(Integer, default=0)
    price_changes: Mapped[int] = mapped_column(Integer, default=0)
    rejected: Mapped[int] = mapped_column(Integer, default=0)
    error: Mapped[str | None] = mapped_column(Text, nullable=True)


class Catalog(Base):
    __tablename__ = "catalogs"
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    store_id: Mapped[str] = mapped_column(ForeignKey("stores.id"))
    title: Mapped[str] = mapped_column(String(256))
    kind: Mapped[str] = mapped_column(String(24))  # aktuel | campaign | discount_list | link_only
    valid_from: Mapped[date | None] = mapped_column(Date, nullable=True)
    valid_to: Mapped[date | None] = mapped_column(Date, nullable=True)
    official_url: Mapped[str] = mapped_column(String(512))
    source_method: Mapped[str] = mapped_column(String(48))
    observed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    data_mode: Mapped[str] = mapped_column(String(8), default="real")
    # Katalog kapağı ve sayfaları (resmî CDN adresleri). pages_json: [{"url","thumb_url","caption","link","verified"}]
    cover_url: Mapped[str | None] = mapped_column(String(512), nullable=True)
    pages_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    # Yayın izi: kataloğun kaynakta İLK görüldüğü an (yayımlanma tarihi olarak kullanılır; bir daha değişmez),
    # son kontrol anı ve içerik özeti (başlık+tarih+sayfalar). Özeti değişmeyen katalog yeniden "yeni" sayılmaz.
    pdf_url: Mapped[str | None] = mapped_column(String(512), nullable=True)
    first_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    last_checked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    content_hash: Mapped[str | None] = mapped_column(String(64), nullable=True)
    # Bölgesel yayın (ör. Onur: istanbul/bursa/trakya). Aynı insertin bölge kopyaları uygulamada TEK kartta birleşir.
    region: Mapped[str | None] = mapped_column(String(32), nullable=True)


class Offer(Base):
    __tablename__ = "offers"
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    store_id: Mapped[str] = mapped_column(ForeignKey("stores.id"), index=True)
    catalog_id: Mapped[str | None] = mapped_column(ForeignKey("catalogs.id"), nullable=True, index=True)
    name: Mapped[str] = mapped_column(String(512))
    brand: Mapped[str | None] = mapped_column(String(128), nullable=True)
    variant: Mapped[str | None] = mapped_column(String(512), nullable=True)
    search_key: Mapped[str] = mapped_column(Text)
    category: Mapped[str] = mapped_column(String(32), index=True)
    category_source: Mapped[str] = mapped_column(String(16))
    price: Mapped[Decimal | None] = mapped_column(Numeric(12, 2), nullable=True)
    price_text: Mapped[str | None] = mapped_column(String(64), nullable=True)
    currency: Mapped[str] = mapped_column(String(3), default="TRY")
    # Mağazanın sayfada yazdığı önceki/liste fiyatı. FIRSAT360 tarafından DOĞRULANMAMIŞTIR.
    store_declared_old_price: Mapped[Decimal | None] = mapped_column(Numeric(12, 2), nullable=True)
    condition: Mapped[str | None] = mapped_column(Text, nullable=True)
    channel: Mapped[str | None] = mapped_column(String(32), nullable=True)  # magaza | online | bilinmiyor
    product_url: Mapped[str | None] = mapped_column(String(512), nullable=True)
    image_url: Mapped[str | None] = mapped_column(String(512), nullable=True)
    image_large_url: Mapped[str | None] = mapped_column(String(512), nullable=True)
    # official_cdn_link: mağazanın resmî CDN adresi; FIRSAT360 görseli kopyalamaz. Yeniden yayın izni ayrıca alınmalı.
    image_rights: Mapped[str] = mapped_column(String(24), default="not_cleared")
    # Görsel doğrulama (imagematch.py): verified | listing | official_json | no_image_on_page | page_empty |
    # no_page | name_mismatch | size_mismatch | none. Yalnızca "verified"/"listing"/"official_json" görsel taşır.
    image_status: Mapped[str | None] = mapped_column(String(24), nullable=True)
    image_page_url: Mapped[str | None] = mapped_column(String(512), nullable=True)  # görselin doğrulandığı sayfa
    image_checked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    image_match_score: Mapped[Decimal | None] = mapped_column(Numeric(5, 3), nullable=True)
    # Ürün sayfasındaki fiyat listedekinden farklıysa (fiyat değiştirilmez, yalnızca işaretlenir)
    page_price_conflict: Mapped[Decimal | None] = mapped_column(Numeric(12, 2), nullable=True)
    page_size_text: Mapped[str | None] = mapped_column(String(32), nullable=True)  # ölçü listede yoksa sayfadan
    valid_from: Mapped[date | None] = mapped_column(Date, nullable=True)
    valid_to: Mapped[date | None] = mapped_column(Date, nullable=True)
    stock_status: Mapped[str] = mapped_column(String(16), default="unknown")
    scope: Mapped[str] = mapped_column(String(32), default="unverified")
    first_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    source_url: Mapped[str] = mapped_column(String(512))
    source_method: Mapped[str] = mapped_column(String(48))
    verification: Mapped[str] = mapped_column(String(48))
    data_mode: Mapped[str] = mapped_column(String(8), default="real")
    published: Mapped[bool] = mapped_column(Boolean, default=True)

    history: Mapped[list["PriceObservation"]] = relationship(back_populates="offer", cascade="all, delete-orphan",
                                                              order_by="PriceObservation.observed_at")


class PriceObservation(Base):
    __tablename__ = "price_observations"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    offer_id: Mapped[str] = mapped_column(ForeignKey("offers.id"), index=True)
    price: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    observed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    source_method: Mapped[str] = mapped_column(String(48))
    offer: Mapped[Offer] = relationship(back_populates="history")


class Event(Base):
    """Tıklama/dönüşüm analitiği (anonim): kimlik, cihaz kimliği veya kişisel veri tutulmaz."""
    __tablename__ = "events"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    kind: Mapped[str] = mapped_column(String(32), index=True)
    store_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    offer_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    catalog_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    app_version: Mapped[str | None] = mapped_column(String(24), nullable=True)
    affiliate: Mapped[bool] = mapped_column(Boolean, default=False)


class HttpCache(Base):
    """Koşullu istek (ETag / Last-Modified) doğrulayıcıları. Değişmeyen kaynak yeniden indirilmez."""
    __tablename__ = "http_cache"
    url: Mapped[str] = mapped_column(String(1024), primary_key=True)
    etag: Mapped[str | None] = mapped_column(String(256), nullable=True)
    last_modified: Mapped[str | None] = mapped_column(String(64), nullable=True)
    content_sha256: Mapped[str | None] = mapped_column(String(64), nullable=True)
    checked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    not_modified_count: Mapped[int] = mapped_column(Integer, default=0)


class Announcement(Base):
    """Kullanıcılara duyurulan yeni katalog grubu (günlük özet). Bir katalog yalnızca BİR kez duyurulur."""
    __tablename__ = "announcements"
    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    title: Mapped[str] = mapped_column(String(128))
    body: Mapped[str] = mapped_column(Text)
    catalog_ids: Mapped[str] = mapped_column(Text)  # JSON listesi
    store_ids: Mapped[str] = mapped_column(Text)    # JSON listesi
    # Push: none (ayarlı değil) | sent | failed
    push_status: Mapped[str] = mapped_column(String(16), default="none")
    push_detail: Mapped[str | None] = mapped_column(Text, nullable=True)
    push_attempts: Mapped[int] = mapped_column(Integer, default=0)
    deliver_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class AnnouncedCatalog(Base):
    __tablename__ = "announced_catalogs"
    catalog_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    # None: ilk kurulumda (veya eski olduğu için) sessizce işaretlendi, bildirim gönderilmedi
    announcement_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class SyncRun(Base):
    """Günlük senkronizasyonun izlenebilir kaydı (her çalıştırma bir satır)."""
    __tablename__ = "sync_runs"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    trigger: Mapped[str] = mapped_column(String(16), default="schedule")
    ok_stores: Mapped[int] = mapped_column(Integer, default=0)
    failed_stores: Mapped[int] = mapped_column(Integer, default=0)
    new_catalogs: Mapped[int] = mapped_column(Integer, default=0)
    announcement_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    summary: Mapped[str | None] = mapped_column(Text, nullable=True)


Index("ix_offers_store_category", Offer.store_id, Offer.category)


def make_engine(url: str):
    kwargs = {"future": True}
    if url.startswith("sqlite"):
        kwargs["connect_args"] = {"check_same_thread": False}
    else:
        kwargs["pool_pre_ping"] = True
    return create_engine(url, **kwargs)


def auto_migrate(engine) -> list[str]:
    """Var olan tablolara modelde olup veritabanında olmayan sütunları ekler (yalnızca ekleme; veri silinmez)."""
    from sqlalchemy import inspect, text
    added = []
    insp = inspect(engine)
    with engine.begin() as conn:
        for table in Base.metadata.sorted_tables:
            if not insp.has_table(table.name):
                continue
            existing = {c["name"] for c in insp.get_columns(table.name)}
            for col in table.columns:
                if col.name in existing:
                    continue
                ddl_type = col.type.compile(dialect=engine.dialect)
                conn.execute(text(f'ALTER TABLE {table.name} ADD COLUMN {col.name} {ddl_type}'))
                added.append(f"{table.name}.{col.name}")
    return added


def make_session_factory(url: str) -> sessionmaker[Session]:
    engine = make_engine(url)
    Base.metadata.create_all(engine)
    auto_migrate(engine)
    return sessionmaker(engine, expire_on_commit=False)


def count(session: Session, model) -> int:
    return session.scalar(select(func.count()).select_from(model)) or 0

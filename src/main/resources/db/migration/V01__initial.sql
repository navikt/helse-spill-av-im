create table inntektsmelding
(
    id                  bigserial primary key,
    fnr                 varchar(32)              not null,
    virksomhetsnummer   varchar(32) default null,
    ekstern_dokument_id uuid                     not null, -- id lagt på av hag
    intern_dokument_id  uuid unique              not null, -- id lagt på av r&r
    innsendt            timestamp with time zone not null, -- når inntektsmeldingen ble sendt inn
    registrert          timestamp with time zone not null, -- når inntektsmeldingen ble registert av spill-av-im
    avsendersystem      varchar(32),
    forste_fravarsdag   date        default null,
    inntektsdato        date        default null,
    data                json                     not null
);

create index im_replay_idx on inntektsmelding (fnr, virksomhetsnummer);
create index im_intern_dokument_id_idx on inntektsmelding (intern_dokument_id);

create table handtering
(
    id                 bigserial primary key,
    fnr                varchar(32)                                              not null,
    vedtaksperiode_id  uuid                                                     not null,
    inntektsmelding_id bigint references inntektsmelding (id) on delete cascade not null,
    handtert           timestamp with time zone                                 not null
);

create index handtering_vedtaksperiode_id on handtering (vedtaksperiode_id);
create index handtering_im_id on handtering (inntektsmelding_id);

create table replay_foresporsel
(
    id                bigserial primary key,
    innsendt          timestamp with time zone not null, -- når forespørselen ble sendt av spleis
    registrert        timestamp with time zone not null, -- når forespørselen ble registert av spill-av-im
    fnr               varchar(32)              not null,
    virksomhetsnummer varchar(32),
    vedtaksperiode_id uuid                     not null
);

create index rf_fnr_idx on replay_foresporsel (fnr);
create index rf_vedtaksperiode_idx on replay_foresporsel (vedtaksperiode_id);

create table replay
(
    id                    bigserial primary key,
    replay_foresporsel_id bigint not null references replay_foresporsel (id) on delete cascade,
    inntektsmelding_id    bigint not null references inntektsmelding (id) on delete cascade
);

create index replay_inntektsmelding_id on replay (inntektsmelding_id);
create index replay_replay_foresporsel_id on replay (replay_foresporsel_id);

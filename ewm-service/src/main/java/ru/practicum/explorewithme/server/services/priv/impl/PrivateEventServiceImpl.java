package ru.practicum.explorewithme.server.services.priv.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.explorewithme.models.event.*;
import ru.practicum.explorewithme.models.request.ParticipationRequestDto;
import ru.practicum.explorewithme.models.request.RequestStatus;
import ru.practicum.explorewithme.server.exceptions.notfound.CategoryNotFoundException;
import ru.practicum.explorewithme.server.exceptions.notfound.EventNotFoundException;
import ru.practicum.explorewithme.server.exceptions.notfound.RequestNotFoundException;
import ru.practicum.explorewithme.server.exceptions.notfound.UserNotFoundException;
import ru.practicum.explorewithme.server.exceptions.requestcondition.RequestConditionException;
import ru.practicum.explorewithme.server.models.*;
import ru.practicum.explorewithme.server.repositories.*;
import ru.practicum.explorewithme.server.services.priv.PrivateEventService;
import ru.practicum.explorewithme.server.utils.mappers.MyMapper;
import ru.practicum.explorewithme.server.utils.mappers.RequestMapper;
import ru.practicum.explorewithme.server.utils.selectioncondition.SearchParam;
import ru.practicum.explorewithme.server.utils.selectioncondition.SelectionConditionForPrivate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ru.practicum.explorewithme.server.utils.ServerUtil.makePageable;
import static ru.practicum.explorewithme.server.utils.mappers.EventMapper.makeUpdatableModelPrivate;
import static ru.practicum.explorewithme.server.utils.mappers.EventMapper.toEvent;
import static ru.practicum.explorewithme.server.utils.mappers.RequestMapper.toRequestDto;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PrivateEventServiceImpl implements PrivateEventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocRepository locRepository;
    private final RequestRepository requestRepository;
    private final FollowersRepository followersRepository;
    private final GroupRepository groupRepository;

    private final MyMapper mapper;

    @Override
    public List<EventShortDto> getEventsByOwnerId(long userId, int from, int size) {
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        List<Event> events = eventRepository.findAllByInitiator_Id(userId, makePageable(from, size));

        log.info("Запрошены Events пользователя с id = {}", userId);
        return events.stream().map(mapper::toEventShort).collect(Collectors.toList());
    }

    @Override
    public EventFullDto updateEvent(long userId, UpdateEventRequest request) {
        Event event = eventRepository.findByInitiator_IdAndId(userId, request.getEventId())
                .orElseThrow(() -> new EventNotFoundException(request.getEventId()));

        Category category;
        if (request.getCategory() != null) {
            category = categoryRepository.findById(request.getCategory()).orElseThrow(() ->
                    new CategoryNotFoundException(request.getCategory()));
        } else {
            category = null;
        }

        if (!List.of(State.PENDING, State.CANCELED).contains(event.getState())) {
            throw new RequestConditionException("Нельзя менять опубликованные и отмененные события");
        }

        makeUpdatableModelPrivate(event, request, category);

        log.info("Event с id = {} обновлен. Новые параметры {}", request.getEventId(), request);
        return mapper.toEventFull(eventRepository.save(event));
    }

    @Override
    public EventFullDto addEvent(long userId, NewEventDto eventDto) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        Category category = categoryRepository.findById(eventDto.getCategory())
                .orElseThrow(() -> new CategoryNotFoundException(eventDto.getCategory()));

        Location location = eventDto.getLocation();

        Optional<Loc> loc = locRepository.findByLatitudeAndLongitude(location.getLat(), location.getLon());

        Event event = eventRepository.save(toEvent(eventDto, category, user, loc));

        log.info("Event {} с id = {} добавлен", event.getTitle(), event.getId());

        return mapper.toEventFull(event);
    }

    @Override
    public EventFullDto getEventByOwnerIdAndEventId(long userId, long eventId) {
        Event event = eventRepository.findByInitiator_IdAndId(userId, eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        log.info("Event {} с id = {} запрошен владельцем с id = {}", event.getTitle(), eventId, userId);

        return mapper.toEventFull(event);
    }

    @Override
    public EventFullDto cancelEventByOwner(long userId, long eventId) {
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        Event event = eventRepository.findByInitiator_IdAndId(userId, eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        switch (event.getState()) {
            case PUBLISHED:
                throw new RequestConditionException("Нельзя отменить опубликованное событие");
            case CANCELED:
                throw new RequestConditionException("Событие уже отменено");
            default:
                log.info("Event с id = {} отменен владельцем", eventId);
                event.setState(State.CANCELED);
                return mapper.toEventFull(event);
        }
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(long userId, long eventId) {
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        eventRepository.findByInitiator_IdAndId(userId, eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        log.info("Запрошен список заявок для Event с id = {}", eventId);
        return requestRepository.findAllByEvent_Id(eventId).stream().map(RequestMapper::toRequestDto)
                .collect(Collectors.toList());
    }

    @Override
    public ParticipationRequestDto confirmRequestForEvent(long userId, long eventId, long reqId) {
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        Event event = eventRepository.findByInitiator_IdAndId(userId, eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        Request request = requestRepository.findById(reqId).orElseThrow(() -> new RequestNotFoundException(reqId));

        if (event.getParticipantLimit() == event.getNumberConfirmed()) {
            requestRepository.rejectedAllRequestsByEventId(eventId);
            throw new RequestConditionException("Лимит участников события достигнут");
        }
        event.setNumberConfirmed(event.getNumberConfirmed() + 1);
        eventRepository.save(event);

        request.setStatus(RequestStatus.CONFIRMED);
        requestRepository.save(request);

        return toRequestDto(request);
    }

    @Override
    public ParticipationRequestDto rejectRequestForEvent(long userId, long eventId, long reqId) {
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        eventRepository.findByInitiator_IdAndId(userId, eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        Request request = requestRepository.findById(reqId).orElseThrow(() -> new RequestNotFoundException(reqId));

        request.setStatus(RequestStatus.REJECTED);

        return toRequestDto(request);
    }

    @Override
    public List<EventFullDto> getEventsWhereParticipant(long userFollowerId, Long userId, SelectionConditionForPrivate selection) {
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        if (userFollowerId == userId) {
            throw new RequestConditionException("Нет доступа");
        }

        QEvent qEvent = QEvent.event;
        List<Long> eventIds;

        Group group = followersRepository.findByPublisher_IdAndFollower_Id(userId, userFollowerId).filter(f ->
                !f.getGroup().getTitle().equals("FOLLOWER")).map(Follower::getGroup).orElseThrow(() ->
                new RequestConditionException("Доступно только для друзей пользователя"));

        Group groupAll = groupRepository.findByUser_IdAndTitleIgnoreCase(userId, "Friends_all").get();

        eventIds = requestRepository.findEventIdsWhereRequestStatusConfirmedAndGroup(userId, group, groupAll);

        SearchParam param = selection.getSearchParametersParticipant(qEvent, eventIds);

        return eventRepository.findAll(param.getBooleanExpression(), param.getPageable()).stream()
                .map(mapper::toEventFull).collect(Collectors.toList());
    }

    @Override
    public List<EventFullDto> getEventsWhereCreator(long userFollowerId, Long userId, SelectionConditionForPrivate selection) {
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        if (userFollowerId == userId) {
            throw new RequestConditionException("Нет доступа");
        }

        followersRepository.findByPublisher_IdAndFollower_Id(userId, userFollowerId).orElseThrow(() ->
                new RequestConditionException("Доступно только для подписчиков"));

        QEvent qEvent = QEvent.event;
        SearchParam param = selection.getSearchParametersCreator(qEvent);

        return eventRepository.findAll(param.getBooleanExpression(), param.getPageable()).stream()
                .map(mapper::toEventFull).collect(Collectors.toList());
    }
}

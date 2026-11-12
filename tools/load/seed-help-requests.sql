-- QS-11 (step 12, increment 2): three years of help requests (~50,000 a year) before the load run.
-- Test setup only: it writes the aggregate's tables directly, as the tests' resets do.
-- 150,000 finished requests, one every 10.5 minutes back from now, with time-ordered (UUIDv7-like)
-- ids, so each is "earlier" than the run's requests; 5,000 cooks, Lena one of them (30 requests);
-- half preparation steps, half ingredients (10 names); a third closed, each with its accepted Help, the
-- rest cancelled; every fifth cancelled one with a note.
set search_path = cooking_assistance;

create temporary table seed as
select g,
       now() - g * interval '630 seconds' as at,
       (lpad(to_hex((extract(epoch from now() - g * interval '630 seconds') * 1000)::bigint), 12, '0')
          || '7' || substr(md5(g::text), 1, 3) || '8' || substr(md5(g::text), 4, 15))::uuid as id,
       case when g % 5000 = 0 then 'f64e07f0-f9e7-4b7a-8695-e7a8b3ef5074'::uuid
            else ('00000000-0000-4000-8000-' || lpad(to_hex(g % 5000), 12, '0'))::uuid end as requester,
       g % 2 = 0 as about_a_step,
       case when g % 3 = 0 then 'CLOSED' else 'CANCELLED' end as status
from generate_series(1, 150000) as g;

insert into help_request (id, requester, title, description, type, recipe, how_to_step, status, version,
                          ingredient, ingredient_name, substitute_requirements)
select id, requester, 'Earlier request ' || g, 'Seeded for QS-11',
       case when about_a_step then 'PREPARATION_STEP_EXPLANATION' else 'INGREDIENT_SUBSTITUTE' end,
       ('00000000-0000-4000-9000-' || lpad(to_hex(g % 700), 12, '0'))::uuid,
       case when about_a_step then ('00000000-0000-4000-a000-' || lpad(to_hex(g % 9), 12, '0'))::uuid end,
       status, 1,
       case when not about_a_step then ('00000000-0000-4000-b000-' || lpad(to_hex(g % 700), 12, '0'))::uuid end,
       case when not about_a_step then (array['crème fraîche', 'bacon', 'butter', 'saffron', 'eggs', 'milk',
                                              'flour', 'buttermilk', 'mascarpone', 'yeast'])[g % 10 + 1] end,
       case when not about_a_step then '200 g' end
from seed;

insert into asked_provider (help_request_id, position, provider_type, deadline, state)
select id, 0, 'COMMUNITY', at + interval '24 hours',
       case when status = 'CLOSED' then 'ANSWERED' else 'TIMED_OUT' end
from seed;

-- a closed request has its accepted Help (INV-05, INV-13), from the Community, in the answer's AsyncAPI shape
insert into help (id, help_request_id, position, provider_type, provider_person, answer_title, answer, accepted)
select md5('help' || g)::uuid, id, 0, 'COMMUNITY', ('00000000-0000-4000-c000-' || lpad(to_hex(g % 300), 12, '0'))::uuid,
       'Earlier answer ' || g,
       case when about_a_step
            then jsonb_build_object('answerType', 'PREPARATION_STEP_EXPLANATION',
                   'recipe', '00000000-0000-4000-9000-' || lpad(to_hex(g % 700), 12, '0'),
                   'howToStep', '00000000-0000-4000-a000-' || lpad(to_hex(g % 9), 12, '0'),
                   'description', 'Seeded explanation', 'images', '[]'::jsonb)
            else jsonb_build_object('answerType', 'INGREDIENT_SUBSTITUTE',
                   'recipe', '00000000-0000-4000-9000-' || lpad(to_hex(g % 700), 12, '0'),
                   'substitute', jsonb_build_array(jsonb_build_object(
                     'ingredient', '00000000-0000-4000-b000-' || lpad(to_hex(g % 700), 12, '0'),
                     'substituteIngredients', jsonb_build_array(
                       jsonb_build_object('name', 'yogurt', 'value', 200, 'unit', 'GRAM')))))
       end,
       true
from seed
where status = 'CLOSED';

insert into help_request_note (help_request_id, position, kind, text, written_at, help_id)
select id, 0, 'CANCELLATION', 'found another way', at + interval '1 hour', null
from seed
where status = 'CANCELLED' and g % 5 = 0;

analyze help_request;
analyze asked_provider;
analyze help;
analyze help_request_note;

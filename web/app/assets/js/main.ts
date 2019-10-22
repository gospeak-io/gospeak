declare const $;

const slugify = (str: string): string => {
    return (str || '')
        .trim()
        .toLowerCase()
        .replace(/[ _+'"]/g, '-')
        .replace(/--/g, '-')
        .normalize('NFD').replace(/[^a-z0-9-]/g, '');
};

// enable global features
declare const autosize;
(function () {
    $('[data-toggle="tooltip"]').tooltip();
    $('[data-toggle="popover"]').popover();
    $('[data-toggle="html-popover"]').each(() => {
        const $el = $(this);
        const $content = $el.find('.content');
        $el.popover({html: true, content: $content});
        $content.remove();
    });
    autosize($('textarea'));
})();

// confirm actions
(function () {
    $('[confirm]').click(e => {
        const text = $(this).attr('confirm') || $(this).attr('title') || 'Confirm?';
        if (!confirm(text)) {
            e.preventDefault();
        }
    });
})();

// autofocus when a modal opens
(function () {
    $('.modal').on('shown.bs.modal', () => {
        const $modal = $(this);
        $modal.find('input[autofocus], textarea[autofocus]').focus();
        autosize.update($modal.find('textarea'));
    });
})();

// disable inputs not in visible pane
(function () {
    // disable input inside hidden tabs
    $('.tab-pane.radio:not(.show)').find('input, textarea, select').attr('disabled', true);

    // enable input inside shown tab
    $('a.radio[data-toggle="tab"], a.radio[data-toggle="pill"]').on('shown.bs.tab', e => {
        $(e.target.getAttribute('href')).find('input, textarea, select').attr('disabled', false);
        if (e.relatedTarget) {
            $(e.relatedTarget.getAttribute('href')).find('input, textarea, select').attr('disabled', true);
        }
    });
})();

// build slug from an other field
(function () {
    const buildSlug = (inputs, prev?: string) => {
        return inputs
            .map(input => prev && input.attr('id') === prev ? input.data('prev') : input.val())
            .filter(value => !!value)
            .map(input => slugify(input))
            .join('-');
    };

    $('input[slug-for]').each(() => {
        const slugInput = $(this);
        const srcInputs = slugInput.attr('slug-for').split(',').map(id => $('#' + id));
        srcInputs.forEach(srcInput => {
            srcInput.change(() => {
                const newSlug = buildSlug(srcInputs);
                const oldSlug = buildSlug(srcInputs, srcInput.attr('id'));
                srcInput.data('prev', srcInput.val());
                const curSlug = slugInput.val();
                if (curSlug === '' || curSlug === oldSlug) {
                    slugInput.val(newSlug);
                }
            });
        });
    });
})();

// remote input validation
(function () {
    $('input[remote-validate]').each(() => {
        const $input = $(this);
        const url = $input.attr('remote-validate');
        if (url) {
            update($input, url); // run on page load
            $input.change(() => update($input, url));
        }
    });

    const update = ($input, url): void => {
        const value = $input.val();
        if (value) {
            $.getJSON(url.replace('%7B%7Binput%7D%7D', value), res => {
                if ($input.val() === value) {
                    removeFeedback($input);
                    if (res.valid) {
                        addValidFeedback($input, res);
                    } else {
                        addInvalidFeedback($input, res);
                    }
                }
            });
        } else {
            removeFeedback($input);
        }
    };

    const removeFeedback = ($input): void => {
        $input.removeClass('is-valid');
        $input.removeClass('is-invalid');
        const $next = $input.next();
        if ($next.hasClass('valid-feedback') || $next.hasClass('invalid-feedback')) {
            $next.remove();
        }
    };

    const addValidFeedback = ($input, res): void => {
        $input.addClass('is-valid');
        if (res.message) {
            $input.after('<span class="valid-feedback">' + res.message + '</span>');
        }
    };

    const addInvalidFeedback = ($input, res): void => {
        $input.addClass('is-invalid');
        if (res.message) {
            $input.after('<span class="invalid-feedback">' + res.message + '</span>');
        }
    };
})();

// https://select2.org/ & https://github.com/select2/select2-bootstrap-theme
(function () {
    const defaultOpts = {
        theme: 'bootstrap',
        allowClear: true
    };
    $('select.select2').each(() => {
        const $select = $(this);
        $select.select2(Object.assign({}, defaultOpts, {
            placeholder: $select.attr('placeholder')
        }));
        addRemoteOptions($select);
    });
    $('select.tags').each(() => {
        const $select = $(this);
        $select.select2(Object.assign({}, defaultOpts, {
            placeholder: $select.attr('placeholder'),
            tags: true
        }));
        addRemoteOptions($select);
    });

    const addRemoteOptions = ($select): void => {
        const remote = $select.attr('remote');
        const remoteReplace = $select.attr('remote-replace');
        if (remote) { // $select input should be populated using remote call response
            if (remoteReplace) { // remote url depends on the value of an other field ("$placeholder:$fieldId")
                const [placeholder, fieldId] = remoteReplace.split(':');
                const $input = $('#' + fieldId);
                $input.change(() => fetchAndSetOptions($select, remote.replace(placeholder, $input.val())));
            } else {
                fetchAndSetOptions($select, remote);
            }
        }
    };

    const fetchAndSetOptions = ($select, url): void => {
        $.getJSON(url, res => {
            const values = ($select.attr('value') || '').split(',').filter(v => v.length > 0);
            $select.find('option[value]').remove(); // remove non empty existing options before adding new ones
            res.map(item => {
                if ($select.find('option[value="' + item.id + '"]').length === 0) { // do not add item if it already exists
                    if (values.indexOf(item.id) > -1) {
                        $select.append('<option value="' + item.id + '" selected>' + item.text + '</option>');
                    } else {
                        $select.append('<option value="' + item.id + '">' + item.text + '</option>');
                    }
                }
            });
        });
    }
})();

// https://uxsolutions.github.io/bootstrap-datepicker/
(function () {
    const DAYS = 0;
    const MONTHS = 1;
    const YEARS = 2;
    const defaultConfig = {
        format: 'dd/mm/yyyy',
        weekStart: 1,
        startView: DAYS,
        minViewMode: DAYS,
        maxViewMode: YEARS,
        language: 'en',
        daysOfWeekHighlighted: '0,6',
        calendarWeeks: true,
        autoclose: true,
        todayHighlight: true,
        toggleActive: true
    };
    $('input.input-date').each(() => {
        $(this).datepicker(Object.assign({}, defaultConfig, {
            defaultViewDate: $(this).attr('startDate')
        }));
    });
})();

// https://unmanner.github.io/imaskjs/
declare const IMask;
(function () {
    $('input.input-time').each(() => {
        IMask(this, {
            mask: 'HH:mm',
            lazy: false,
            blocks: {
                HH: {mask: IMask.MaskedRange, from: 0, to: 23},
                mm: {mask: IMask.MaskedRange, from: 0, to: 59}
            }
        });
    });
    // clear empty input-time so backend will get empty values instead of placeholder ones
    $('form').submit(e => {
        $(e.target).find('input.input-time').each(() => {
            const $input = $(this);
            if ($input.val() === '__:__') {
                $input.val('');
            }
        });
    });
})();

// http://cloudfour.github.io/hideShowPassword/
/* (function () {
    $('input[type="password"]').each(() => {
        $(this).hideShowPassword({
            show: false,
            innerToggle: true,
            triggerOnToggle: 'focus'
        });
    });
})(); */

// inputImageUrl
(function () {
    $('.input-imageurl').each(() => {
        const $elt = $(this);
        const $input = $elt.find('input[type="text"]');
        const $preview = $elt.find('.preview');
        update($input, $preview); // run on page load
        $input.change(() => update($input, $preview));
    });

    const update = ($input, $preview): void => {
        if ($input.val() === '') {
            $preview.hide();
        } else {
            $preview.attr('src', $input.val());
            $preview.show();
        }
    };
})();

// markdown input
(function () {
    const fetchHtml = (md) => {
        return fetch('/ui/utils/markdown-to-html', {method: 'POST', body: md}).then(function (res) {
            return res.text();
        });
    };

    $('.markdown-editor').each(() => {
        const previewTab = $(this).find('a[data-toggle="tab"].preview');
        const textarea = $(this).find('textarea');
        const previewPane = $(this).find('.tab-pane.preview');
        const loadingHtml = previewPane.html();
        previewTab.on('show.bs.tab', () => {
            const md = textarea.val();
            fetchHtml(md).then(html => {
                previewPane.html(html);
            });
        });
        previewTab.on('hidden.bs.tab', () => {
            previewPane.html(loadingHtml);
        });
    });
})();

// template input & data
(function () {
    $('.template-data').each(() => {
        const $elt = $(this);
        const ref = $elt.attr('data-ref');
        const target = $elt.attr('data-target');
        const $target = target ? $('#' + target) : undefined;
        updateData($elt, ref);
        if ($target) {
            $target.change(() => updateData($elt, $target.val()));
        }
    });
    $('.template-editor').each(() => {
        const $elt = $(this);
        const ref = $elt.attr('data-ref');
        const target = $elt.attr('data-target');
        const $target = target ? $('#' + target) : undefined;
        const previewTab = $elt.find('a[data-toggle="tab"].preview');
        const $input = $elt.find('textarea,input[type="text"]');
        const previewPane = $elt.find('.tab-pane.preview');
        const loadingHtml = previewPane.html();
        const markdown = $elt.attr('data-markdown') === 'true';
        previewTab.on('show.bs.tab', () => {
            const r = $target ? $target.val() : ref;
            updateTemplate($input, r, markdown, previewPane);
        });
        previewTab.on('hidden.bs.tab', () => {
            previewPane.html(loadingHtml);
        });
        if ($target) {
            $target.change(() => {
                if (previewPane.hasClass('active')) {
                    updateTemplate($input, $target.val(), markdown, previewPane);
                }
            });
        }
    });

    const updateData = ($elt, ref): void => {
        if (ref) {
            fetchData(ref).then(res => {
                $elt.find('.json-viewer').html(JSON.stringify(res.data, null, 2));
                $elt.show();
            });
        } else {
            $elt.hide();
        }
    };

    const updateTemplate = ($input, ref, markdown, previewPane): void => {
        const tmpl = $input.val();
        fetchTemplate(tmpl, ref, markdown).then(tmpl => {
            if (tmpl.error) {
                console.warn('Template error', tmpl.error);
            }
            previewPane.html(tmpl.result);
        });
    };

    const fetchData = (ref) => fetch('/ui/utils/template-data/' + ref).then(res => res.json());

    const fetchTemplate = (tmpl, ref, markdown) => fetch('/ui/utils/render-template', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            template: tmpl,
            ref: ref,
            markdown: markdown
        })
    }).then(res => res.json());
})();

// embed input & display
(function () {
    const fetchEmbedCode = (url) => fetch('/ui/utils/embed?url=' + url).then(res => res.text());

    const inputFetchEmbedCode = (input, target) => {
        const url = input.val();
        if (!!url) {
            target.html('<div class="d-flex justify-content-center m-5"><div class="spinner-border" role="status"><span class="sr-only">Loading...</span></div></div>');
            fetchEmbedCode(url).then(html => {
                // verify that input value haven't changed since the start of the query
                if (input.val() === url) {
                    target.html(html);
                }
            }, () => {
                if (input.val() === url) {
                    target.html('');
                }
            });
        } else {
            target.html('');
        }
    };

    $('.embed-editor').each(() => {
        const input = $(this).find('input');
        const embed = $(this).find('.embed');
        inputFetchEmbedCode(input, embed);
        input.change(() => inputFetchEmbedCode(input, embed));
    });
    $('.embed-display').each(() => {
        const embed = $(this);
        const url = embed.attr('data-url');
        fetchEmbedCode(url).then(html => embed.html(html));
    });
})();

// omni-search with https://twitter.github.io/typeahead.js/
declare const Bloodhound;
(function () {
    $('[data-omni-search]').each(() => {
        const $search = $(this);
        const baseUrl = $search.attr('data-omni-search');
        const datasetBuilder = (url, title) => ({
            name: url,
            limit: 20,
            source: new Bloodhound({
                datumTokenizer: Bloodhound.tokenizers.whitespace,
                queryTokenizer: Bloodhound.tokenizers.whitespace,
                remote: {
                    wildcard: '%QUERY',
                    url: baseUrl + '/' + url + '?q=%QUERY'
                }
            }),
            display: item => item.text, // will be set in the input
            templates: {
                header: '<b class="tt-header">' + title + '</b>',
                suggestion: item => '<a href="' + item.url + '">' + item.text + '</a>',
                pending: '<b class="tt-header">' + title + ' <i class="fas fa-circle-notch fa-spin"></i></b>',
                notFound: '<b class="tt-header">' + title + '</b>'
            }
        });

        $search.typeahead(
            {minLength: 2, hint: true, highlight: true},
            datasetBuilder('speakers', 'Speakers'),
            datasetBuilder('proposals', 'Proposals'),
            datasetBuilder('partners', 'Partners'),
            datasetBuilder('events', 'Events')
        );
        $search.bind('typeahead:select', (evt, item) => {
            if (item.url) {
                window.location.href = item.url; // navigate to url when item is selected (with keyboard)
            }
        });
    });
})();

// https://craig.is/killing/mice
declare const Mousetrap;
(function () {
    $('[data-hotkey]').each(() => {
        const $hotkey = $(this);
        if ($hotkey.hasClass('tt-hint')) return; // 'tt-hint' is a duplicated field from omni-search, so it's ignored
        const keys = $hotkey.attr('data-hotkey');
        Mousetrap.bind(keys.split(','), (event, key) => {
            const tagName = $hotkey.prop('tagName');
            if (tagName === 'INPUT') {
                $hotkey.focus();
                return false;
            } else if (tagName === 'A' && $hotkey.attr('href')) {
                window.location.href = $hotkey.attr('href'); // to prevent opening in new tab when "_blank" attribute
                return false;
            } else if (tagName === 'DIV' && $hotkey.hasClass('modal')) {
                $hotkey.modal('toggle');
                return false;
            } else {
                console.warn('"' + keys + '" was pressed by no default binding for ' + tagName, $hotkey);
            }
        });
    });
})();

// GMapPlace picker (https://developers.google.com/maps/documentation/javascript/examples/places-autocomplete?hl=fr)
declare const google;
const GMapPlacePicker = (function () {
    const initMap = ($map) => {
        const map = new google.maps.Map($map.get(0), {
            center: {lat: -33.8688, lng: 151.2195},
            zoom: 13
        });
        const marker = new google.maps.Marker({
            map: map,
            anchorPoint: new google.maps.Point(0, -29)
        });
        const infowindow = new google.maps.InfoWindow();
        return {
            $map: $map,
            map: map,
            marker: marker,
            infowindow: infowindow
        };
    };

    const toggleMap = (mapData, location): void => {
        if (location && location.lat && location.lng) {
            showMap(mapData, location);
        } else {
            hideMap(mapData);
        }
    };

    const showMap = (mapData, location): void => {
        const point = {lat: parseFloat(location.lat), lng: parseFloat(location.lng)};
        mapData.$map.show();
        google.maps.event.trigger(mapData.map, 'resize');
        mapData.infowindow.close();
        mapData.marker.setVisible(false);
        mapData.map.setCenter(point);
        mapData.map.setZoom(15);
        mapData.marker.setPosition(point);
        mapData.marker.setVisible(true);
        mapData.infowindow.setContent(
            '<strong>' + location.name + '</strong><br>' +
            location.streetNo + ' ' + location.street + '<br>' +
            location.postalCode + ' ' + location.locality + ', ' + location.country
        );
        mapData.infowindow.open(mapData.map, mapData.marker);
    };

    const hideMap = (mapData): void => mapData.$map.hide();

    const fields = ['id', 'name', 'streetNo', 'street', 'postalCode', 'locality', 'country', 'formatted', 'lat', 'lng', 'url', 'website', 'phone', 'utcOffset'];

    const writeForm = ($elt, location): void => {
        fields.forEach(field => {
            $elt.find('input.gmapplace-' + field).val(location ? location[field] : '');
        });
    };

    const readForm = ($elt) => {
        const location = {};
        fields.forEach(field => {
            location[field] = $elt.find('input.gmapplace-' + field).val();
        });
        return location;
    };

    const toLocation = (place) => {
        const getSafe = (elt, field) => elt && elt[field] ? elt[field] : '';

        const toAddress = (components) => {
            const getByType = (components, type) => {
                const c = components.find(e => e.types.indexOf(type) >= 0);
                return c && c.long_name;
            };

            return {
                streetNumber: getByType(components, 'street_number'),
                route: getByType(components, 'route'),
                postalCode: getByType(components, 'postal_code'),
                locality: getByType(components, 'locality'),
                country: getByType(components, 'country'),
                administrativeArea: {
                    level1: getByType(components, 'administrative_area_level_1'),
                    level2: getByType(components, 'administrative_area_level_2'),
                    level3: getByType(components, 'administrative_area_level_3'),
                    level4: getByType(components, 'administrative_area_level_4'),
                    level5: getByType(components, 'administrative_area_level_5')
                },
                sublocality: {
                    level1: getByType(components, 'sublocality_level_1'),
                    level2: getByType(components, 'sublocality_level_2'),
                    level3: getByType(components, 'sublocality_level_3'),
                    level4: getByType(components, 'sublocality_level_4'),
                    level5: getByType(components, 'sublocality_level_5')
                }
            };
        };

        const address = toAddress(place.address_components);
        const loc = place && place.geometry && place.geometry.location;
        return {
            id: getSafe(place, 'place_id'),
            name: getSafe(place, 'name'),
            streetNo: getSafe(address, 'streetNumber'),
            street: getSafe(address, 'route'),
            postalCode: getSafe(address, 'postalCode'),
            locality: getSafe(address, 'locality'),
            country: getSafe(address, 'country'),
            formatted: getSafe(place, 'formatted_address'),
            lat: loc ? loc.lat().toString() : '',
            lng: loc ? loc.lng().toString() : '',
            url: getSafe(place, 'url'),
            website: getSafe(place, 'website'),
            phone: getSafe(place, 'international_phone_number'),
            utcOffset: getSafe(place, 'utc_offset').toString()
        };
    };

    const initAutocomplete = ($elt, $input, mapData): void => {
        const autocomplete = new google.maps.places.Autocomplete($input.get(0));
        autocomplete.addListener('place_changed', function () {
            const place = autocomplete.getPlace(); // cf https://developers.google.com/maps/documentation/javascript/reference/places-service?hl=fr#PlaceResult
            const location = toLocation(place);
            writeForm($elt, location);
            toggleMap(mapData, location);
        });
    };

    return {
        init: () => {
            $('.gmapplace-input').each(function () {
                const $elt = $(this);
                const $input = $elt.find('input[type="text"]');
                const $map = $elt.find('.map');
                const mapData = initMap($map);
                const location = readForm($elt);
                toggleMap(mapData, location);
                initAutocomplete($elt, $input, mapData);
                // prevent form submit on enter
                $input.on('keydown', function (e) {
                    if (e && e.keyCode === 13) {
                        e.preventDefault();
                    }
                });
                // clear form when input is cleared
                $input.on('change', function () {
                    if ($input.val() === '') {
                        const location = null;
                        writeForm($elt, location);
                        toggleMap(mapData, location);
                    }
                });
            });
        }
    };
})();

function googleMapsInit() {
    GMapPlacePicker.init();
}

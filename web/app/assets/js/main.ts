declare const $;

interface ApiResponse<T> {
    data: T
}

// JS redirect urls (because it's a PITA to do in DNS ^^)
(function () {
    const url = window.location.href;
    const redirectUrl = url
        .replace('gospeak.fr', 'gospeak.io')
        .replace('www.gospeak.io', 'gospeak.io')
        .replace('http://gospeak.io', 'https://gospeak.io');
    if (redirectUrl !== url) {
        window.location.replace(redirectUrl);
    }
})();

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
    $('[data-toggle="html-popover"]').each(function () {
        const $el = $(this);
        const $content = $el.find('.content');
        $el.popover({html: true, content: $content});
        $content.remove();
    });
    autosize($('textarea'));
})();

// confirm actions
(function () {
    $('[confirm]').click(function (e) {
        const text = $(this).attr('confirm') || $(this).attr('title') || 'Confirm?';
        if (!confirm(text)) {
            e.preventDefault();
        }
    });
})();

// stop event propagation when needed (ex: links inside a collapse)
(function () {
    $('.no-propagation').click(function (e) {
        e.stopImmediatePropagation();
    });
})();

// autofocus when a modal opens
(function () {
    $('.modal').on('shown.bs.modal', function () {
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

    $('input[slug-for]').each(function () {
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
                    slugInput.change();
                }
            });
        });
    });
})();

// remote input validation
(function () {
    $('input[remote-validate]').each(function () {
        const $input = $(this);
        const url = $input.attr('remote-validate');
        if (url) {
            update($input, url); // run on page load
            $input.change(() => update($input, url));
        }
    });

    interface ValidationResult {
        message: string
        valid: boolean
    }

    function update($input, url): void {
        const value = $input.val();
        if (value) {
            $.getJSON(url.replace('%7B%7Binput%7D%7D', value), (res: ApiResponse<ValidationResult>) => {
                if ($input.val() === value) {
                    removeFeedback($input);
                    if (res.data.valid) {
                        addValidFeedback($input, res.data);
                    } else {
                        addInvalidFeedback($input, res.data);
                    }
                }
            });
        } else {
            removeFeedback($input);
        }
    }

    function removeFeedback($input): void {
        $input.removeClass('is-valid');
        $input.removeClass('is-invalid');
        const $next = $input.next();
        if ($next.hasClass('valid-feedback') || $next.hasClass('invalid-feedback')) {
            $next.remove();
        }
    }

    function addValidFeedback($input, res: ValidationResult): void {
        $input.addClass('is-valid');
        if (res.message) {
            $input.after('<span class="valid-feedback">' + res.message + '</span>');
        }
    }

    function addInvalidFeedback($input, res: ValidationResult): void {
        $input.addClass('is-invalid');
        if (res.message) {
            $input.after('<span class="invalid-feedback">' + res.message + '</span>');
        }
    }
})();

// https://select2.org/ & https://github.com/select2/select2-bootstrap-theme
(function () {
    const defaultOpts = {
        theme: 'bootstrap',
        allowClear: true
    };
    $('select.select2').each(function () {
        const $select = $(this);
        $select.select2(Object.assign({}, defaultOpts, {
            placeholder: $select.attr('placeholder')
        }));
        addRemoteOptions($select);
    });
    $('select.tags').each(function () {
        const $select = $(this);
        $select.select2(Object.assign({}, defaultOpts, {
            placeholder: $select.attr('placeholder'),
            tags: true
        }));
        addRemoteOptions($select);
    });

    function addRemoteOptions($select): void {
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
    }

    interface SuggestedItem {
        id: string
        text: string
    }

    function fetchAndSetOptions($select, url): void {
        $.getJSON(url, (res: ApiResponse<SuggestedItem[]>) => {
            const values: string[] = ($select.attr('value') || '').split(',').filter(v => v.length > 0); // currently selected values
            const valuesSuggestions = values.map(v => ({id: v, text: v}));
            const options = res.data.concat(valuesSuggestions).filter((v, i, arr) => arr.indexOf(v) === i); // add values not in suggestions
            $select.find('option[value]').remove(); // remove non empty existing options before adding new ones
            options.map(item => {
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
    $('input.input-date').each(function () {
        $(this).datepicker(Object.assign({}, defaultConfig, {
            defaultViewDate: $(this).attr('startDate')
        }));
    });
})();

// https://unmanner.github.io/imaskjs/
declare const IMask;
(function () {
    $('input.input-time').each(function () {
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
        $(e.target).find('input.input-time').each(function () {
            const $input = $(this);
            if ($input.val() === '__:__') {
                $input.val('');
            }
        });
    });
})();

// http://cloudfour.github.io/hideShowPassword/
/* (function () {
    $('input[type="password"]').each(function ()> {
        $(this).hideShowPassword({
            show: false,
            innerToggle: true,
            triggerOnToggle: 'focus'
        });
    });
})(); */

// inputImg
declare const cloudinary;
(function () {
    $('.input-imageurl').each(function () {
        const $elt = $(this);
        const $input = $elt.find('input[type="text"]');
        const $preview = $elt.find('.preview');
        update($input, $preview); // run on page load
        $input.change(() => update($input, $preview));
    });

    // see https://cloudinary.com/documentation/upload_widget
    $('.cloudinary-img-widget').each(function () {
        const $elt = $(this);
        const $btn = $elt.find('button.upload');
        const $input = $elt.find('input[type="hidden"]');
        const $preview = $elt.find('.preview');

        const $gallery = $elt.find('.gallery');
        initGallery($gallery, $input, $preview);

        update($input, $preview); // run on page load

        const cloudName = $btn.attr('data-cloud-name');
        const uploadPreset = $btn.attr('data-upload-preset');
        const apiKey = $btn.attr('data-api-key');
        const signUrl = $btn.attr('data-sign-url');
        const folder = $btn.attr('data-folder');
        const name = toPublicId($btn.attr('data-name') || '');
        const tagsStr = $btn.attr('data-tags');
        const maxFilesStr = $btn.attr('data-max-files');
        const ratioStr = $btn.attr('data-ratio');
        const dynamicName = $btn.attr('data-dynamic-name');
        const tags = tagsStr ? tagsStr.split(',') : undefined;
        const maxFiles = maxFilesStr ? parseInt(maxFilesStr) : undefined;
        const ratio = ratioStr ? parseFloat(ratioStr) : undefined;

        const $dynamicNameInput = dynamicName ? $('#' + dynamicName) : undefined;
        const $selectSearch = $gallery ? $gallery.find('input') : undefined;

        // see https://cloudinary.com/documentation/upload_widget#upload_widget_options
        const opts = {
            cloudName: cloudName,
            uploadPreset: uploadPreset,
            apiKey: apiKey,
            uploadSignature: apiKey ? generateSignature(signUrl) : undefined,
            // upload params
            folder: folder,
            publicId: name,
            tags: tags,
            resourceType: 'image',
            clientAllowedFormats: ['png', 'jpg', 'jpeg', 'gif', 'svg'], // see https://cloudinary.com/documentation/image_transformations#supported_image_formats
            // format params
            multiple: maxFiles !== 1,
            maxFiles: maxFiles,
            cropping: ratio !== undefined,
            croppingAspectRatio: ratio,
            showSkipCropButton: ratio !== undefined,
            croppingShowDimensions: ratio !== undefined,
        };

        const cloudinaryWidget = cloudinary.createUploadWidget(opts, (error, result) => {
            if (!error && result && result.event === 'success') {
                const imageUrl: string = cloudinaryUrl(result.info, cloudName, ratio);
                $input.val(imageUrl);
                addToGallery($gallery, parseImageUrl(imageUrl));
                update($input, $preview);
            }
        });

        $btn.click(function (e) {
            e.preventDefault();
            const publicId = toPublicId(($selectSearch && $selectSearch.val()) || ($dynamicNameInput && $dynamicNameInput.val()) || '');
            opts.publicId = publicId;

            if (!apiKey) {
                // needed as unsigned upload can't override, use signed upload instead (add `creds` in app config, see UploadConf)
                cloudinaryWidget.update({publicId: `${publicId}-${Date.now()}`});
            } else {
                cloudinaryWidget.update({publicId: publicId});
            }

            cloudinaryWidget.open();
        });
    });

    function update($input, $preview): void {
        if ($input.val() === '') {
            $preview.hide();
        } else {
            $preview.attr('src', $input.val());
            $preview.show();
        }
    }

    function cloudinaryUrl(info, cloudName: string, ratio?: number): string {
        let transformations = '';
        if (info.coordinates && info.coordinates.custom && info.coordinates.custom.length === 1) {
            const [x, y, width, height] = info.coordinates.custom[0];
            transformations = `${transformations}/x_${x},y_${y},w_${width},h_${height},c_crop`;
        } else if (ratio) {
            transformations = `${transformations}/ar_${ratio},c_crop`;
        }
        return `https://res.cloudinary.com/${cloudName}/${info.resource_type}/${info.type}${transformations}/v${info.version}/${info.public_id}.${info.format}`;
    }

    function generateSignature(signUrl: string) {
        return (callback, params_to_sign): void => $.ajax({
            url: signUrl,
            type: 'GET',
            data: params_to_sign
        }).then(res => callback(res.data));
    }

    function initGallery($gallery, $input, $preview): void {
        if ($gallery) {
            const $search = $gallery.find('input');
            const url = $gallery.attr('data-remote');
            fetch(url).then(res => res.json()).then(json => {
                json.data
                    .map(parseImageUrl)
                    .sort((a, b) => a.publicId.localeCompare(b.publicId))
                    .forEach(image => addToGallery($gallery, image));
            });
            $gallery.on('shown.bs.collapse', function () {
                $search.focus();
            });
            $gallery.on('hidden.bs.collapse', function () {
                $search.val('');
                $gallery.find('.logo').each(function (i, logo) {
                    logo.style.display = 'inline-block';
                });
            });
            $search.on('keyup', function (e) {
                const search = $(this).val().toLowerCase();
                $gallery.find('.logo').each(function (i, logo) {
                    const title = (logo.getAttribute('title') || logo.getAttribute('data-original-title') || '').toLowerCase();
                    if (title.includes(search)) {
                        logo.style.display = 'inline-block';
                    } else {
                        logo.style.display = 'none';
                    }
                });
            });
            $gallery.on('click', '.logo', function (e) {
                e.preventDefault();
                const image = $(this).find('img').attr('src');
                $input.val(image);
                update($input, $preview);
                $gallery.collapse('hide');
            });
        }
    }

    function addToGallery($gallery, image: { url: string, publicId: string }) {
        if ($gallery) {
            $gallery.append(`<div class="logo" title="${image.publicId}" style="display: inline-block; cursor: pointer">
                <img src="${image.url}" alt="${image.publicId}" style="height: 50px; margin-top: 5px; margin-right: 5px;">
            </div>`);
            $gallery.find('.logo').tooltip('enable');
        }
    }

    function parseImageUrl(url: string): { url: string, publicId: string } {
        const parts = url.split('?')[0].split('/').filter(p => p.length > 0);
        const publicId = decodeURIComponent(parts[parts.length - 1].split('.')[0]).replace(/-/g, ' ');
        return {url, publicId};
    }

    function toPublicId(str: string): string {
        return str.replace(/[ \/?&#]/g, '-').replace(/-+/g, '-').toLowerCase();
    }
})();

// markdown input
(function () {
    $('.markdown-editor').each(function () {
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

    function fetchHtml(md: string): Promise<string> {
        return fetch('/ui/utils/markdown-to-html', {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(md)
        }).then(res => res.json()).then(json => json.data);
    }
})();

// template input & data
(function () {
    $('.template-data').each(function () {
        const $elt = $(this);
        const ref = $elt.attr('data-ref');
        const target = $elt.attr('data-target');
        const $target = target ? $('#' + target) : undefined;
        updateData($elt, ref);
        if ($target) {
            $target.change(() => updateData($elt, $target.val()));
        }
    });
    $('.template-editor').each(function () {
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

    function updateData($elt, ref): void {
        if (ref) {
            fetchData(ref).then(res => {
                $elt.find('.json-viewer').html(JSON.stringify(res.data, null, 2));
                $elt.show();
            });
        } else {
            $elt.hide();
        }
    }

    function updateTemplate($input, ref, markdown, previewPane): void {
        const tmpl = $input.val();
        fetchTemplate(tmpl, ref, markdown).then(tmpl => {
            if (tmpl.error) {
                previewPane.html(`<pre class="alert alert-warning mb-0" role="alert">${tmpl.error}</pre>`);
            } else {
                previewPane.html(tmpl.result);
            }
        });
    }

    function fetchData(ref) {
        return fetch('/ui/utils/template-data/' + ref).then(res => res.json()).then(json => json.data);
    }

    function fetchTemplate(tmpl, ref, markdown) {
        return fetch('/ui/utils/render-template', {
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
        }).then(res => res.json()).then(json => json.data ? json.data : {error: json.message});
    }
})();

// embed input & display
(function () {
    $('.embed-editor').each(function () {
        const input = $(this).find('input');
        const embed = $(this).find('.embed');
        inputFetchEmbedCode(input, embed);
        input.change(() => inputFetchEmbedCode(input, embed));
    });
    $('.embed-display').each(function () {
        const embed = $(this);
        const url = embed.attr('data-url');
        fetchEmbedCode(url).then(html => embed.html(html));
    });

    function inputFetchEmbedCode(input, target): void {
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
    }

    function fetchEmbedCode(url) {
        return fetch('/ui/utils/embed?url=' + url).then(res => res.json()).then(json => json.data);
    }
})();

// omni-search with https://twitter.github.io/typeahead.js/
declare const Bloodhound;
(function () {
    $('[data-omni-search]').each(function () {
        const $search = $(this);
        const baseUrl = $search.attr('data-omni-search');

        function datasetBuilder(url, title) {
            return {
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
            };
        }

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

// svg injector (cf home page)
(function () {
    $.HSCore.components.HSSVGIngector.init('.js-svg-injector');
})();

// typing animation (cf home page), https://github.com/mattboldt/typed.js
declare const Typed;
(function () {
    $('.js-typed').each(function () {
        const $elt = $(this);
        const strings = ($elt.attr('data-strings') || '').split(',');
        new Typed('.js-typed', {
            strings: strings,
            typeSpeed: 60,
            loop: true,
            backSpeed: 25,
            backDelay: 1500
        });
    });
})();

// https://craig.is/killing/mice
declare const Mousetrap;
(function () {
    $('[data-hotkey]').each(function () {
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
    function initMap($map) {
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
    }

    function toggleMap(mapData, location): void {
        if (location && location.lat && location.lng) {
            showMap(mapData, location);
        } else {
            hideMap(mapData);
        }
    }

    function showMap(mapData, location): void {
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
    }

    function hideMap(mapData): void {
        mapData.$map.hide();
    }

    const fields = ['id', 'name', 'streetNo', 'street', 'postalCode', 'locality', 'country', 'formatted', 'lat', 'lng', 'url', 'website', 'phone', 'utcOffset'];

    function writeForm($elt, location): void {
        fields.forEach(field => {
            $elt.find('input.gmapplace-' + field).val(location ? location[field] : '');
        });
    }

    function readForm($elt) {
        const location = {};
        fields.forEach(field => {
            location[field] = $elt.find('input.gmapplace-' + field).val();
        });
        return location;
    }

    function toLocation(place) {
        function getSafe(elt, field) {
            return elt && elt[field] ? elt[field] : '';
        }

        function toAddress(components) {
            function getByType(components, type) {
                const c = components.find(e => e.types.indexOf(type) >= 0);
                return c && c.long_name;
            }

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
        }

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
    }

    function initAutocomplete($elt, $input, mapData): void {
        const autocomplete = new google.maps.places.Autocomplete($input.get(0));
        autocomplete.addListener('place_changed', function () {
            const place = autocomplete.getPlace(); // cf https://developers.google.com/maps/documentation/javascript/reference/places-service?hl=fr#PlaceResult
            const location = toLocation(place);
            writeForm($elt, location);
            toggleMap(mapData, location);
        });
    }

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

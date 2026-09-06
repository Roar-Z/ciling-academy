-- =====================================================
-- 基础功能词 / 常用词 补全脚本
-- 作用：让"普通翻译"（基于 dict_word 逐词解析）覆盖
--       the / a / of / in / jumps / dogs 等基础词
-- 说明：
--   - level='other', difficulty=1
--   - 不影响学习/复习模块（按 difficulty IN(2,4) 取词）
--   - UNIQUE KEY uk_word, 使用 INSERT IGNORE 可重复执行
-- =====================================================

INSERT IGNORE INTO dict_word (word, phonetic, pos, meaning, difficulty, level, is_core) VALUES
-- 冠词
('a',       '/ə/',      'art.', '一(个)',                                          1, 'other', 1),
('an',      '/æn/',     'art.', '一(个)',                                          1, 'other', 1),
('the',     '/ðə/',     'art.', '这;那',                                            1, 'other', 1),

-- 介词
('in',      '/ɪn/',     'prep.', '在…里',                                          1, 'other', 1),
('on',      '/ɒn/',     'prep.', '在…上',                                          1, 'other', 1),
('at',      '/æt/',     'prep.', '在;处于',                                         1, 'other', 1),
('by',      '/baɪ/',    'prep.', '在…旁;被;到…为止',                                1, 'other', 1),
('for',     '/fɔːr/',   'prep.', '为了;对于',                                       1, 'other', 1),
('to',      '/tuː/',    'prep.', '向;到;给',                                        1, 'other', 1),
('from',    '/frɒm/',   'prep.', '来自;从',                                         1, 'other', 1),
('with',    '/wɪð/',    'prep.', '和…一起;用',                                      1, 'other', 1),
('of',      '/ɒv/',     'prep.', '…的(表示所属/部分)',                               1, 'other', 1),
('about',   '/əˈbaʊt/', 'prep.', '关于;大约',                                       1, 'other', 1),
('over',    '/ˈəʊvər/', 'prep.', '在…上方;超过',                                    1, 'other', 1),
('under',   '/ˈʌndər/', 'prep.', '在…下面',                                         1, 'other', 1),
('into',    '/ˈɪntuː/', 'prep.', '进入;到…里',                                      1, 'other', 1),
('between', '/bɪˈtwiːn/','prep.', '在…之间',                                        1, 'other', 1),
('through', '/θruː/',   'prep.', '穿过;通过',                                       1, 'other', 1),
('before',  '/bɪˈfɔːr/','prep.', '在…之前',                                         1, 'other', 1),
('after',   '/ˈɑːftər/','prep.', '在…之后',                                         1, 'other', 1),
('since',   '/sɪns/',   'prep.', '自从;自从…以来',                                  1, 'other', 1),
('until',   '/ənˈtɪl/', 'prep.', '直到',                                            1, 'other', 1),
('around',  '/əˈraʊnd/','prep.', '在…周围;大约',                                    1, 'other', 1),

-- 代词
('i',       '/aɪ/',     'pron.', '我',                                             1, 'other', 1),
('you',     '/juː/',    'pron.', '你;你们',                                         1, 'other', 1),
('he',      '/hiː/',    'pron.', '他',                                             1, 'other', 1),
('she',     '/ʃiː/',    'pron.', '她',                                             1, 'other', 1),
('it',      '/ɪt/',     'pron.', '它',                                             1, 'other', 1),
('we',      '/wiː/',    'pron.', '我们',                                           1, 'other', 1),
('they',    '/ðeɪ/',    'pron.', '他/她/它们',                                       1, 'other', 1),
('me',      '/miː/',    'pron.', '我(宾格)',                                        1, 'other', 1),
('him',     '/hɪm/',    'pron.', '他(宾格)',                                        1, 'other', 1),
('her',     '/hɜːr/',   'pron.', '她(宾格);她的',                                    1, 'other', 1),
('us',      '/ʌs/',     'pron.', '我们(宾格)',                                      1, 'other', 1),
('them',    '/ðem/',    'pron.', '他/她/它们(宾格)',                                1, 'other', 1),
('my',      '/maɪ/',    'pron.', '我的',                                           1, 'other', 1),
('your',    '/jɔːr/',   'pron.', '你的;你们的',                                     1, 'other', 1),
('his',     '/hɪz/',    'pron.', '他的',                                           1, 'other', 1),
('our',     '/aʊər/',   'pron.', '我们的',                                         1, 'other', 1),
('their',   '/ðeər/',   'pron.', '他/她/它们的',                                    1, 'other', 1),
('this',    '/ðɪs/',    'pron.', '这;这个',                                         1, 'other', 1),
('that',    '/ðæt/',    'pron.', '那;那个',                                         1, 'other', 1),
('these',   '/ðiːz/',   'pron.', '这些',                                           1, 'other', 1),
('those',   '/ðəʊz/',   'pron.', '那些',                                           1, 'other', 1),

-- be 动词
('am',      '/æm/',     'v.',   '是',                                              1, 'other', 1),
('is',      '/ɪz/',     'v.',   '是',                                              1, 'other', 1),
('are',     '/ɑːr/',    'v.',   '是',                                              1, 'other', 1),
('was',     '/wɒz/',    'v.',   '是(过去式)',                                       1, 'other', 1),
('were',    '/wɜːr/',   'v.',   '是(过去式)',                                       1, 'other', 1),
('be',      '/biː/',    'v.',   '是;成为',                                          1, 'other', 1),
('been',    '/bɪn/',    'v.',   '是(过去分词)',                                     1, 'other', 1),
('being',   '/ˈbiːɪŋ/', 'v.',   '是(现在分词)',                                     1, 'other', 1),

-- 助动词
('do',      '/duː/',    'v.',   '做;干',                                            1, 'other', 1),
('does',    '/dʌz/',    'v.',   '做(三单)',                                         1, 'other', 1),
('did',     '/dɪd/',    'v.',   '做(过去)',                                         1, 'other', 1),
('have',    '/hæv/',    'v.',   '有;让',                                            1, 'other', 1),
('has',     '/hæz/',    'v.',   '有(三单)',                                         1, 'other', 1),
('had',     '/hæd/',    'v.',   '有(过去)',                                         1, 'other', 1),
('will',    '/wɪl/',    'aux.', '将;会',                                            1, 'other', 1),
('would',   '/wʊd/',    'aux.', '会;愿意(过去式)',                                  1, 'other', 1),

-- 情态
('can',     '/kæn/',    'aux.', '能;会',                                            1, 'other', 1),
('could',   '/kʊd/',    'aux.', '能;会(过去式)',                                    1, 'other', 1),
('may',     '/meɪ/',    'aux.', '可能;可以',                                        1, 'other', 1),
('might',   '/maɪt/',   'aux.', '可能(过去式)',                                     1, 'other', 1),
('must',    '/mʌst/',   'aux.', '必须;一定',                                        1, 'other', 1),
('should',  '/ʃʊd/',    'aux.', '应该;将会',                                        1, 'other', 1),

-- 疑问/关系
('who',     '/huː/',    'pron.', '谁',                                             1, 'other', 1),
('what',    '/wɒt/',    'pron.', '什么',                                           1, 'other', 1),
('which',   '/wɪtʃ/',   'pron.', '哪个;哪些',                                       1, 'other', 1),
('where',   '/weər/',   'adv.',  '哪里',                                           1, 'other', 1),
('when',    '/wen/',    'adv.',  '什么时候',                                        1, 'other', 1),
('how',     '/haʊ/',    'adv.',  '怎样;如何',                                       1, 'other', 1),

-- 连词
('and',     '/ænd/',    'conj.', '和;并且',                                         1, 'other', 1),
('or',      '/ɔːr/',    'conj.', '或者;否则',                                       1, 'other', 1),
('but',     '/bʌt/',    'conj.', '但是;然而',                                       1, 'other', 1),
('so',      '/səʊ/',    'conj.', '所以',                                            1, 'other', 1),
('if',      '/ɪf/',     'conj.', '如果',                                            1, 'other', 1),
('because', '/bɪˈkɒz/', 'conj.', '因为',                                            1, 'other', 1),
('as',      '/æz/',     'conj.', '当;像;作为',                                      1, 'other', 1),

-- 常用副词
('not',     '/nɒt/',    'adv.',  '不;没有',                                         1, 'other', 1),
('very',    '/ˈveri/',  'adv.',  '非常;很',                                         1, 'other', 1),
('too',     '/tuː/',    'adv.',  '也;太',                                           1, 'other', 1),
('also',    '/ˈɔːlsəʊ/','adv.',  '也',                                             1, 'other', 1),
('only',    '/ˈəʊnli/', 'adv.',  '只;仅仅',                                         1, 'other', 1),
('just',    '/dʒʌst/',  'adv.',  '只是;刚刚',                                       1, 'other', 1),

-- 常用形容词
('good',    '/ɡʊd/',    'adj.',  '好的',                                            1, 'other', 1),
('bad',     '/bæd/',    'adj.',  '坏的;糟糕的',                                     1, 'other', 1),
('big',     '/bɪɡ/',    'adj.',  '大的',                                            1, 'other', 1),
('small',   '/smɔːl/',  'adj.',  '小的',                                            1, 'other', 1),
('new',     '/njuː/',   'adj.',  '新的',                                            1, 'other', 1),
('old',     '/əʊld/',   'adj.',  '旧的;年老的',                                     1, 'other', 1),
('high',    '/haɪ/',    'adj.',  '高的',                                            1, 'other', 1),
('low',     '/ləʊ/',    'adj.',  '低的;少的',                                        1, 'other', 1),

-- 常用名词
('time',    '/taɪm/',   'n.',    '时间;次',                                          1, 'other', 1),
('year',    '/jɪər/',   'n.',    '年',                                              1, 'other', 1),
('day',     '/deɪ/',    'n.',    '天;日',                                           1, 'other', 1),
('world',   '/wɜːrld/', 'n.',    '世界',                                            1, 'other', 1),
('life',    '/laɪf/',   'n.',    '生活;生命',                                        1, 'other', 1),
('home',    '/həʊm/',   'n.',    '家',                                              1, 'other', 1),
('work',    '/wɜːrk/',  'n.',    '工作',                                            1, 'other', 1),
('way',     '/weɪ/',    'n.',    '路;方法',                                         1, 'other', 1);

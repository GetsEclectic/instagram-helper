import pandas as pd
import lightgbm as lgb
import json
import numpy as np
from sklearn import preprocessing
from sklearn.metrics import roc_auc_score
from sklearn.model_selection import KFold
import re


non_word_pattern = re.compile(r'\W+')


def read_large_csv(filename):
    large_pd = pd.DataFrame()

    for dataframe_chunk in pd.read_csv(filename, iterator=True, chunksize=1000):
        large_pd = large_pd.append(dataframe_chunk)

    return large_pd


def remove_non_word_characters_and_nan_to_empty_string(string):
    if isinstance(string, str):
        return non_word_pattern.sub('', string)
    else:
        return ''


def train_model_on_instagram4k_data():
    data = read_large_csv("../resources/instagram4k_export.csv")

    # values to predict
    target = data.engaged

    # turn json into dataframe columns
    deserialized_json_series = pd.DataFrame.from_records(data.json.apply(json.loads))
    data = data.drop(columns=['json'])
    data = pd.concat([data, deserialized_json_series], axis=1)

    # integer encode categorical features
    columns_to_integer_encode = ['action_type', 'source']
    for column in columns_to_integer_encode:
        le = preprocessing.LabelEncoder()
        data[column] = le.fit_transform(data[column])

    # derive some features
    data['has_zip'] = data.zip.apply(lambda x: x == "")

    # remove non numeric and non boolean features
    valid_features = [f for f in data.columns if f not in
                ['id', 'requested_username', 'insert_date', 'liked','followed_back','engaged', 'profile_pic_url',
                 'hd_profile_pic_url_info', 'hd_profile_pic_versions', 'username', 'biography', 'full_name',
                 'external_url', 'profile_pic_id', 'external_lynx_url', 'zip', 'category', 'city_name', 'public_email',
                 'address_street', 'direct_messaging', 'public_phone_number', 'business_contact_method',
                 'public_phone_country_code']]

    # print(data.direct_messaging.value_counts())
    # quit()

    # scale_pos_weight = num negative / num positive
    value_counts = data.engaged.value_counts()
    scale_pos_weight = value_counts[0] / value_counts[1]

    dtrain = lgb.Dataset(data=data[valid_features],
                         label=target, free_raw_data=False)

    dtrain.construct()
    oof_preds = np.zeros(data.shape[0])

    folds = KFold(n_splits=5, shuffle=True, random_state=1)

    for trn_idx, val_idx in folds.split(data):
        lgb_params = {
            'objective': 'binary',
            'verbose': -1,
            # 'scale_pos_weight': scale_pos_weight
        }

        clf = lgb.train(
            params=lgb_params,
            train_set=dtrain.subset(trn_idx),
            valid_sets=dtrain.subset(val_idx),
            num_boost_round=10000,
            early_stopping_rounds=100,
            verbose_eval=0
        )

        oof_preds[val_idx] = clf.predict(dtrain.data.iloc[val_idx])
        print(roc_auc_score(target.iloc[val_idx],
                            oof_preds[val_idx]))

    data['predictions'] = oof_preds

    print('overall score: %9.6f'
          % (roc_auc_score(target, data['predictions'])))


train_model_on_instagram4k_data()
